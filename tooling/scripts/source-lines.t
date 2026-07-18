#!/usr/bin/env perl

use strict;
use warnings;
use File::Path qw(make_path);
use File::Temp qw(tempdir);
use IPC::Open3 qw(open3);
use Symbol qw(gensym);
use Test::More;

my $script = 'tooling/scripts/source-lines.pl';

subtest '前端软提示线只报告不失败' => sub {
    my $root = fixture_root('frontend/admin/src');
    write_lines("$root/Soft.ts", 301);

    my ($status, $stdout) = run_script($root);

    is($status, 0, '超过 300 行但不超过 500 行时成功');
    like($stdout, qr/^301\t\Q$root\/Soft.ts\E$/m, '输出软提示文件');
};

subtest '前端硬失败线在 report 模式下不失败' => sub {
    my $root = fixture_root('frontend/admin/src');
    write_lines("$root/Hard.ts", 501);

    my ($status) = run_script($root);
    my ($report_status, $stdout) = run_script('--report', $root);

    is($status, 1, '超过 500 行时失败');
    is($report_status, 0, 'report 模式始终成功');
    like($stdout, qr/^501\t\Q$root\/Hard.ts\E$/m, 'report 模式仍输出文件');
};

subtest '后端使用 500 和 700 双阈值' => sub {
    my $soft_root = fixture_root('backend/archive-server/src/main/java');
    write_lines("$soft_root/Soft.java", 501);
    is((run_script($soft_root))[0], 0, '后端 501 行只提示');

    my $hard_root = fixture_root('backend/archive-server/src/main/java');
    write_lines("$hard_root/Hard.java", 701);
    is((run_script($hard_root))[0], 1, '后端 701 行失败');
};

subtest '跨行模板字符串内的注释标记按代码统计' => sub {
    my $root = fixture_root('frontend/admin/src');
    my $file = "$root/Literal.ts";
    write_lines($file, 296);
    append_file(
        $file,
        "const value = `\n/* literal block marker */\n// literal line marker\n`;\nconst after = 1;\n",
    );

    my ($status, $stdout) = run_script('--report', $root);

    is($status, 0, 'report 模式成功');
    like($stdout, qr/^301\t\Q$file\E$/m, '模板字符串的五行均被统计');
};

subtest 'Java 文本块内的注释标记按代码统计' => sub {
    my $root = fixture_root('backend/archive-server/src/main/java');
    my $file = "$root/TextBlock.java";
    write_lines($file, 496);
    append_file(
        $file,
        qq{String value = """\n/* literal block marker */\n// literal line marker\n""";\nint after = 1;\n},
    );

    my ($status, $stdout) = run_script('--report', $root);

    is($status, 0, 'report 模式成功');
    like($stdout, qr/^501\t\Q$file\E$/m, 'Java 文本块的五行均被统计');
};

done_testing;

sub fixture_root {
    my ($relative) = @_;
    my $temp = tempdir(CLEANUP => 1);
    my $root = "$temp/$relative";
    make_path($root);
    return $root;
}

sub write_lines {
    my ($file, $count) = @_;
    open my $handle, '>', $file or die "无法写入 $file: $!\n";
    print {$handle} "const line_$_ = $_;\n" for 1 .. $count;
    close $handle;
}

sub append_file {
    my ($file, $content) = @_;
    open my $handle, '>>', $file or die "无法写入 $file: $!\n";
    print {$handle} $content;
    close $handle;
}

sub run_script {
    my @arguments = @_;
    my $stderr = gensym;
    my $pid = open3(undef, my $stdout_handle, $stderr, $^X, $script, @arguments);
    my $stdout = do { local $/; <$stdout_handle> // '' };
    my $stderr_output = do { local $/; <$stderr> // '' };
    waitpid($pid, 0);
    return ($? >> 8, $stdout, $stderr_output);
}
