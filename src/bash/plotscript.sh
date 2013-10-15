#! /bin/bash
# Plot .csv time on x axis, maxWindow on y1 axis, and delays on y2 axis, target delay as well
# use: ./plotscript.sh infile.csv 50000


echo "
reset
set terminal postscript eps enhanced solid 'Helvetica' 10
set datafile separator ';'
set yrange [0:600000]
set y2range [0:120000]
set title 'Comparison between MaxWindow and OurDelay'
set xlabel 'time [{/Symbol m}s]'
set ylabel 'MaxWindow [B]'
set y2label 'OurDelay [{/Symbol m}s]'
set y2tics
set format x '%15.0f'
set key left top
set output '$1.eps'
f(x) = $2
plot '$1' every 2::1 using 1:6 lc rgb 'red' axes x1y2 title 'our delay', '$1' every 2::1 using 1:18 lc rgb 'green' axes x1y1 title 'max window', f(x) lc rgb 'blue' axes x1y2 title 'target delay'
" > plot.gp && gnuplot plot.gp
