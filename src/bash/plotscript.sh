#! /bin/bash
#Plots every *.csv in the folder.

echo "about to plot each *.CSV in the folder."
echo "script will ask you for target delays to plot"
echo "enter it or enter 's' (without quotes ofc) to skip this file"
for f in *.csv
do
	echo "enter target delay for $f"
	read targetdelay
	if [ "$targetdelay" != "s" ];
		then
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
			set output '$f.window.delays.eps'
			f(x) = $targetdelay
			plot '$f' every 5::1 using 1:6 lc rgb 'red' axes x1y2 title 'our delay', '$f' every 5::1 using 1:18 lc rgb 				'green' axes x1y1 title 'max window', f(x) lc rgb 'blue' axes x1y2 title 'target delay'
			set output '$f.delays.rtt.eps'
			set ylabel 'RTT estimaded [ms]'
			set title 'Comparison between Estimated RTT and OurDelay'
			set yrange [0:200]
			plot '$f' every 5::1 using 1:6 lc rgb 'red' axes x1y2 title 'our delay', f(x) lc rgb 'blue' axes x1y2 title 'targe 				delay', '$f' every 5::1 using 1:16 lc rgb 'green' axes x1y1 title 'RTT'
			" > plot.gp && gnuplot plot.gp
	fi
done
mkdir plots
mv *.eps plots/
