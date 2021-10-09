#!/usr/bin/perl -w

use strict;
use warnings;

my $file = 'list.txt';
open my $info, $file or die "Could not open $file: $!";

print "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources xmlns:tools=\"http://schemas.android.com/tools\">\n";
my $tab = "    ";

my $header = "";
while( my $line = <$info>) {
    if ($line =~ m/^\#/) {
        $header = $tab . $tab . "<!-- " . $line . " -->";
    }
    elsif ($line =~ m/^e\d\d\d:/) {
        if ($header) {
            print $header;
            $header = "";
        }
        my @values = split(': ', $line);
        my $code = uc($values[0]);
        my @names = split(",", $values[1]);
        foreach my $name (@names) {
            chomp $name;
            $name =~ tr/ //ds;
            $name =~ tr/-/_/ds;
            print $tab . "<string translatable=\"false\" tools:ignore=\"UnusedResources\" name=\"glyph__" . $name . "\">" . "\\u" . $code . "</string>\n";
        }
        
    }
}

print "</resources>\n";

close $info;
