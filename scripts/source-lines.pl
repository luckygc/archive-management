#!/usr/bin/env perl

use strict;
use warnings;
use File::Find qw(find);

my $report_only = grep { $_ eq '--report' } @ARGV;
my @roots = grep { -d $_ } qw(server/src/main/java web/src frontend-core/src preview);
my @files;

find(
    {
        no_chdir => 1,
        wanted   => sub {
            return unless -f $_;
            return unless /\.(?:java|ts|vue|go|css)$/;
            push @files, $File::Find::name;
        },
    },
    @roots
);

my @results = map { [effective_lines($_), $_] } @files;
@results = sort { $b->[0] <=> $a->[0] || $a->[1] cmp $b->[1] } @results;

my @violations;
for my $result (@results) {
    my ($lines, $file) = @{$result};
    my $limit = $file =~ m{^(?:web|frontend-core)/} ? 300 : 500;
    next unless $lines > $limit;
    push @violations, $result;
    print "$lines\t$file\n";
}

exit 0 if $report_only || !@violations;

print STDERR "生产源码存在 " . scalar(@violations) . " 个明显超出目标的文件\n";
exit 1;

sub effective_lines {
    my ($file) = @_;
    open my $handle, '<', $file or die "无法读取 $file: $!\n";

    my $block_comment = 0;
    my $html_comment  = 0;
    my $count         = 0;
    while (my $line = <$handle>) {
        my $code = strip_comments($line, \$block_comment, \$html_comment);
        $count++ if $code =~ /\S/;
    }
    close $handle;
    return $count;
}

sub strip_comments {
    my ($line, $block_comment_ref, $html_comment_ref) = @_;
    my $code  = '';
    my $quote = '';
    my $index = 0;

    while ($index < length $line) {
        if ($$block_comment_ref) {
            my $end = index($line, '*/', $index);
            return $code if $end < 0;
            $$block_comment_ref = 0;
            $index = $end + 2;
            next;
        }

        if ($$html_comment_ref) {
            my $end = index($line, '-->', $index);
            return $code if $end < 0;
            $$html_comment_ref = 0;
            $index = $end + 3;
            next;
        }

        my $character = substr($line, $index, 1);
        if ($quote ne '') {
            $code .= $character;
            if ($character eq '\\') {
                $index++;
                $code .= substr($line, $index, 1) if $index < length $line;
            } elsif ($character eq $quote) {
                $quote = '';
            }
            $index++;
            next;
        }

        if ($character eq q{"} || $character eq q{'} || $character eq q{`}) {
            $quote = $character;
            $code .= $character;
            $index++;
            next;
        }
        if (substr($line, $index, 4) eq '<!--') {
            $$html_comment_ref = 1;
            $index += 4;
            next;
        }
        if (substr($line, $index, 2) eq '/*') {
            $$block_comment_ref = 1;
            $index += 2;
            next;
        }
        last if substr($line, $index, 2) eq '//';

        $code .= $character;
        $index++;
    }

    return $code;
}
