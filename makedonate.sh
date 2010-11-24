#!/bin/sh
#
# Convert the project into the "donate" version
# The only difference is the package name, which cannot be the same from the original version
#

ROOT=$(dirname $0)

if [ "$1" = "-u" ]; then
	# UNDO THE CONVERSION
	if [ ! -d $ROOT/src/com/googlecode/droidwall/donate ]; then
		echo Error - Not converted!
		exit 1
	fi
	echo UNDO - Moving java files
	mv $ROOT/src/com/googlecode/droidwall/donate/*.java $ROOT/src/com/googlecode/droidwall/
	echo UNDO - Fixing package name on java files
	sed -i "s/package com.googlecode.droidwall.donate;/package com.googlecode.droidwall;/" $ROOT/src/com/googlecode/droidwall/*.java || exit
	sed -i "s/import com.googlecode.droidwall.donate/import com.googlecode.droidwall/" $ROOT/src/com/googlecode/droidwall/*.java || exit
	echo UNDO - Fixing package name on AndroidManifest.xml
	sed -i "s/com.googlecode.droidwall.donate/com.googlecode.droidwall/" $ROOT/AndroidManifest.xml || exit
	rmdir $ROOT/src/com/googlecode/droidwall/donate
	echo UNDO - Done!
	exit 0
fi

# Convert
if [ -d $ROOT/src/com/googlecode/droidwall/donate ]; then
	echo Error - Already converted!
	exit 1
fi
mkdir $ROOT/src/com/googlecode/droidwall/donate || exit
echo Moving java files
mv $ROOT/src/com/googlecode/droidwall/*.java $ROOT/src/com/googlecode/droidwall/donate/
echo Fixing package name on java files
sed -i "s/package com.googlecode.droidwall;/package com.googlecode.droidwall.donate;/" $ROOT/src/com/googlecode/droidwall/donate/*.java || exit
sed -i "s/import com.googlecode.droidwall/import com.googlecode.droidwall.donate/" $ROOT/src/com/googlecode/droidwall/donate/*.java || exit
echo Fixing package name on AndroidManifest.xml
sed -i "s/com.googlecode.droidwall/com.googlecode.droidwall.donate/" $ROOT/AndroidManifest.xml || exit
echo Done!
