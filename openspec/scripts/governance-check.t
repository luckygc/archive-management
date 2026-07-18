#!/usr/bin/env perl

use strict;
use utf8;
use warnings;
use Encode qw(decode);
use File::Basename qw(dirname);
use File::Path qw(make_path);
use File::Temp qw(tempdir);
use IO::Select;
use IPC::Open3 qw(open3);
use Symbol qw(gensym);

BEGIN {
    binmode STDOUT, ':encoding(UTF-8)';
    binmode STDERR, ':encoding(UTF-8)';
}

use Test::More;

my $script = 'openspec/scripts/governance-check.pl';

subtest '只报告任务全部完成但尚未归档的活动 change' => sub {
    my $root = fixture_root();
    write_valid_history_notice($root);
    write_file("$root/openspec/README.md", "# 规格索引\n");
    write_file("$root/openspec/changes/completed/tasks.md", "- [x] 已完成任务\n");
    write_file("$root/openspec/changes/active/tasks.md", "- [ ] 待完成任务\n");
    write_file("$root/openspec/changes/archive/archived/tasks.md", "- [x] 已归档任务\n");

    my ($status, $stdout, $stderr) = run_script($root);

    is($status, 1, '存在完成但未归档的 change 时退出 1');
    like($stderr, qr/completed/, '报告完成但未归档的 completed change');
    like($stderr, qr/任务已全部完成但仍位于活动 change/, '报告包含约定的精确诊断短语');
    unlike($stderr, qr/(?<![[:alnum:]_-])active(?![[:alnum:]_-])/i, '不报告仍有未完成任务的 active change');
    unlike(
        $stderr,
        qr/(?<![[:alnum:]_-])(?:archive|archived)(?![[:alnum:]_-])/i,
        '不把 archive 目录作为活动 change 报告',
    );
    is($stdout, '', '存在完成但未归档的 change 时标准输出为空');
};

subtest '同一次执行报告规格索引的缺失项和过期项' => sub {
    my $root = fixture_root();
    write_valid_history_notice($root);
    write_file("$root/openspec/specs/alpha/spec.md", "# Alpha\n");
    write_file("$root/openspec/specs/beta/spec.md", "# Beta\n");
    write_file(
        "$root/openspec/README.md",
        "# 规格索引\n\n- `specs/alpha/spec.md`\n- `specs/stale/spec.md`\n",
    );

    my ($status, $stdout, $stderr) = run_script($root);

    is($status, 1, '规格索引漂移时退出 1');
    like($stderr, qr{specs/beta/spec\.md}, '报告实际存在但索引缺失的 beta');
    like($stderr, qr{specs/stale/spec\.md}, '报告索引存在但规格缺失的 stale');
    is($stdout, '', '规格索引违规时标准输出为空');
};

subtest '规格索引顺序变化不构成违规' => sub {
    my $root = fixture_root();
    write_valid_history_notice($root);
    write_file("$root/openspec/specs/alpha/spec.md", "# Alpha\n");
    write_file("$root/openspec/specs/beta/spec.md", "# Beta\n");
    write_file(
        "$root/openspec/README.md",
        "# 规格索引\n\n- `specs/beta/spec.md`\n- `specs/alpha/spec.md`\n",
    );

    my ($status, $stdout, $stderr) = run_script($root);

    is($status, 0, '索引项目仅调整顺序时成功');
    is($stdout, '', '索引顺序变化时标准输出为空');
    is($stderr, '', '索引顺序变化不产生违规诊断');
};

subtest '规格索引包含非法 UTF-8 时给出单条可定位诊断' => sub {
    my $root = fixture_root();
    write_valid_history_notice($root);
    write_raw_file("$root/openspec/README.md", "\xFF\xFE");

    my ($status, $stdout, $stderr) = run_script($root);

    is($status, 1, '规格索引包含非法 UTF-8 时退出 1');
    is($stdout, '', '非法 UTF-8 违规时标准输出为空');
    like($stderr, qr{openspec/README\.md}, '诊断可定位非法 UTF-8 的规格索引');
    my @diagnostics = grep { length } split /\R/, $stderr;
    is(scalar @diagnostics, 1, '非法 UTF-8 只产生一条压缩诊断');
    unlike($stderr, qr/does not map to Unicode|at scripts\/governance-check\.pl line/, '不泄漏 Perl 解码 warning');
};

subtest '历史资料 README 缺失时失败' => sub {
    my $root = fixture_root();
    write_file("$root/openspec/README.md", "# 规格索引\n");

    my ($status, $stdout, $stderr) = run_script($root);

    is($status, 1, '历史资料 README 缺失时退出 1');
    like($stderr, qr{docs/superpowers/README\.md}, '诊断指出缺失的历史资料 README');
    is($stdout, '', '历史资料 README 缺失时标准输出为空');
};

subtest '历史资料 README 缺少声明时失败' => sub {
    my $root = fixture_root();
    write_file("$root/openspec/README.md", "# 规格索引\n");
    write_file("$root/docs/superpowers/README.md", "# Superpowers 文档\n");

    my ($status, $stdout, $stderr) = run_script($root);

    is($status, 1, '历史资料 README 缺少精确声明时退出 1');
    like($stderr, qr{docs/superpowers/README\.md}, '诊断可定位缺少历史声明的 README');
    is($stdout, '', '历史资料 README 缺少声明时标准输出为空');
};

subtest '历史资料 README 含精确声明时通过' => sub {
    my $root = fixture_root();
    write_file("$root/openspec/README.md", "# 规格索引\n");
    write_valid_history_notice($root);

    my ($status, $stdout, $stderr) = run_script($root);

    is($status, 0, '历史资料 README 含精确声明时成功');
    is($stdout, '', '历史资料声明合规时标准输出为空');
    is($stderr, '', '合规的历史资料声明不产生违规诊断');
};

subtest '一次执行汇总多类治理违规' => sub {
    my $root = fixture_root();
    write_file("$root/docs/superpowers/README.md", "# 缺少历史资料声明\n");
    write_file("$root/openspec/changes/completed/tasks.md", "- [x] 已完成任务\n");
    write_file("$root/openspec/specs/alpha/spec.md", "# Alpha\n");
    write_file("$root/openspec/specs/beta/spec.md", "# Beta\n");
    write_file(
        "$root/openspec/README.md",
        "# 规格索引\n\n- `specs/alpha/spec.md`\n- `specs/stale/spec.md`\n",
    );

    my ($status, $stdout, $stderr) = run_script($root);

    is($status, 1, '同时存在多类治理违规时退出 1');
    like($stderr, qr/任务已全部完成但仍位于活动 change/, '汇总完成但未归档的 change 违规');
    like($stderr, qr{specs/beta/spec\.md}, '汇总规格索引缺失项');
    like($stderr, qr{specs/stale/spec\.md}, '汇总规格索引过期项');
    like($stderr, qr{docs/superpowers/README\.md}, '汇总可定位历史资料声明违规');
    is($stdout, '', '同时存在多类治理违规时标准输出为空');
};

done_testing;

sub fixture_root {
    return tempdir(CLEANUP => 1);
}

sub write_valid_history_notice {
    my ($root) = @_;
    write_file("$root/docs/superpowers/README.md", "# Superpowers 文档\n\n历史资料，非当前规范。\n");
}

sub write_file {
    my ($file, $content) = @_;
    make_path(dirname($file));
    open my $handle, '>:encoding(UTF-8)', $file or die "无法写入 $file: $!\n";
    print {$handle} $content;
    close $handle or die "无法关闭 $file: $!\n";
}

sub write_raw_file {
    my ($file, $content) = @_;
    make_path(dirname($file));
    open my $handle, '>:raw', $file or die "无法写入 $file: $!\n";
    print {$handle} $content;
    close $handle or die "无法关闭 $file: $!\n";
}

sub run_script {
    my ($root) = @_;
    my $stderr = gensym;
    my $pid = open3(undef, my $stdout_handle, $stderr, $^X, $script, $root);
    binmode $stdout_handle, ':raw';
    binmode $stderr, ':raw';

    my $selector = IO::Select->new($stdout_handle, $stderr);
    my $stdout_bytes = '';
    my $stderr_bytes = '';
    my %output_for = (
        fileno($stdout_handle) => \$stdout_bytes,
        fileno($stderr)        => \$stderr_bytes,
    );

    while (my @ready = $selector->can_read) {
        for my $handle (@ready) {
            my $file_number = fileno($handle);
            my $read = sysread($handle, my $chunk, 8192);
            die "无法读取治理检查器输出: $!\n" if !defined $read;
            if ($read == 0) {
                $selector->remove($handle);
                close $handle;
                next;
            }
            ${$output_for{$file_number}} .= $chunk;
        }
    }

    waitpid($pid, 0);
    my $wait_status = $?;
    my $status = $wait_status == -1
        ? 255
        : ($wait_status & 127) ? 128 + ($wait_status & 127)
        :                        $wait_status >> 8;
    return ($status, decode('UTF-8', $stdout_bytes), decode('UTF-8', $stderr_bytes));
}
