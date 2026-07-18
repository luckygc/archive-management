#!/usr/bin/env perl

use strict;
use utf8;
use warnings;
use Encode qw(decode FB_CROAK);
use File::Spec;

BEGIN {
    binmode STDOUT, ':encoding(UTF-8)';
    binmode STDERR, ':encoding(UTF-8)';
}

my $root = shift // '.';
my @violations;

check_active_changes($root, \@violations);
check_spec_index($root, \@violations);
check_history_notice($root, \@violations);

if (@violations) {
    print STDERR join("\n", @violations), "\n";
    exit 1;
}

exit 0;

sub check_active_changes {
    my ($repository_root, $violations) = @_;
    my $changes_dir = File::Spec->catdir($repository_root, 'openspec', 'changes');
    return if !-d $changes_dir;

    my $changes;
    if (!opendir $changes, $changes_dir) {
        push @{$violations}, "openspec/changes/: 无法读取目录: $!";
        return;
    }

    my @names = sort readdir $changes;
    closedir $changes;

    for my $name (@names) {
        next if $name eq '.' || $name eq '..' || $name eq 'archive';
        my $change_dir = File::Spec->catdir($changes_dir, $name);
        next if !-d $change_dir;

        my $tasks_path = File::Spec->catfile($change_dir, 'tasks.md');
        next if !-e $tasks_path;
        my $tasks = read_utf8_file($tasks_path, "openspec/changes/$name/tasks.md", $violations);
        next if !defined $tasks;

        my @checkboxes = $tasks =~ /^[ \t]*[-*][ \t]+\[([ xX])\]/gm;
        next if !@checkboxes;
        next if grep { $_ eq ' ' } @checkboxes;

        push @{$violations},
            "openspec/changes/$name/tasks.md: 任务已全部完成但仍位于活动 change";
    }
}

sub check_spec_index {
    my ($repository_root, $violations) = @_;
    my %actual;
    my $specs_dir = File::Spec->catdir($repository_root, 'openspec', 'specs');

    if (-d $specs_dir) {
        if (opendir my $specs, $specs_dir) {
            my @names = sort readdir $specs;
            closedir $specs;
            for my $name (@names) {
                next if $name eq '.' || $name eq '..';
                my $spec_path = File::Spec->catfile($specs_dir, $name, 'spec.md');
                $actual{"specs/$name/spec.md"} = 1 if -f $spec_path;
            }
        } else {
            push @{$violations}, "openspec/specs/: 无法读取目录: $!";
        }
    }

    my %indexed;
    my $readme_path = File::Spec->catfile($repository_root, 'openspec', 'README.md');
    my $readme = read_utf8_file($readme_path, 'openspec/README.md', $violations);
    if (defined $readme) {
        $indexed{$1} = 1 while $readme =~ /`(specs\/[^`\/\r\n]+\/spec\.md)`/g;
    }

    for my $path (sort keys %actual) {
        next if $indexed{$path};
        push @{$violations}, "openspec/README.md: 规格索引缺少 $path";
    }
    for my $path (sort keys %indexed) {
        next if $actual{$path};
        push @{$violations}, "openspec/README.md: 规格索引包含不存在的 $path";
    }
}

sub check_history_notice {
    my ($repository_root, $violations) = @_;
    my $path = File::Spec->catfile($repository_root, 'docs', 'superpowers', 'README.md');
    my $content = read_utf8_file($path, 'docs/superpowers/README.md', $violations);
    return if !defined $content;
    return if index($content, '历史资料，非当前规范') >= 0;

    push @{$violations}, 'docs/superpowers/README.md: 缺少历史资料声明';
}

sub read_utf8_file {
    my ($path, $display_path, $violations) = @_;
    my $handle;
    if (!open $handle, '<:raw', $path) {
        push @{$violations}, "$display_path: 无法读取: $!";
        return;
    }

    local $/;
    my $bytes = <$handle>;
    if (!close $handle) {
        push @{$violations}, "$display_path: 无法关闭: $!";
        return;
    }

    my $content;
    my $decoded = eval {
        $content = decode('UTF-8', $bytes, FB_CROAK);
        1;
    };
    if (!$decoded) {
        push @{$violations}, "$display_path: 内容不是合法 UTF-8";
        return;
    }
    return $content;
}
