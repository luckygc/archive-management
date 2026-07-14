#!/usr/bin/env perl

use strict;
use warnings;
use File::Find qw(find);

my $report_only = grep { $_ eq '--report' } @ARGV;
my @requested_roots = grep { $_ ne '--report' } @ARGV;
my @roots = grep { -d $_ } (
    @requested_roots
        ? @requested_roots
        : qw(server/src/main/java web/src frontend-core/src preview)
);
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
    my ($soft_limit, $hard_limit) =
        $file =~ m{(?:^|/)(?:web|frontend-core)(?:/|$)} ? (300, 500) : (500, 700);
    next unless $lines > $soft_limit;
    print "$lines\t$file\n";
    push @violations, $result if $lines > $hard_limit;
}

exit 0 if $report_only || !@violations;

print STDERR "生产源码存在 " . scalar(@violations) . " 个明显超出目标的文件\n";
exit 1;

sub effective_lines {
    my ($file) = @_;
    open my $handle, '<', $file or die "无法读取 $file: $!\n";

    my $block_comment = 0;
    my $html_comment  = 0;
    my $quote         = '';
    my $count         = 0;
    while (my $line = <$handle>) {
        my $code = strip_comments($line, \$block_comment, \$html_comment, \$quote);
        $count++ if $code =~ /\S/;
    }
    close $handle;
    return $count;
}

sub strip_comments {
    my ($line, $block_comment_ref, $html_comment_ref, $quote_ref) = @_;
    my $code  = '';
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

        if ($$quote_ref ne '') {
            if ($$quote_ref eq '"""' && substr($line, $index, 3) eq '"""') {
                $code .= '"""';
                $$quote_ref = '';
                $index += 3;
                next;
            }
            my $character = substr($line, $index, 1);
            $code .= $character;
            if ($$quote_ref ne '"""' && $character eq '\\') {
                $index++;
                $code .= substr($line, $index, 1) if $index < length $line;
            } elsif ($$quote_ref ne '"""' && $character eq $$quote_ref) {
                $$quote_ref = '';
            }
            $index++;
            next;
        }

        my $character = substr($line, $index, 1);
        if (substr($line, $index, 3) eq '"""') {
            $$quote_ref = '"""';
            $code .= '"""';
            $index += 3;
            next;
        }
        if ($character eq q{"} || $character eq q{'} || $character eq q{`}) {
            $$quote_ref = $character;
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
