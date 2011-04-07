#!/bin/sh
#
# Unix Shell Script for CDL to BOOLEAN conversion v0.1, April 6 2011
#
# Written by Arnaud Hubaux <ahubaux@gmail.com>
#
# USAGE
# sh fcdl2bool <cdl_input_folder> [<bool_output_folder>]
#
# WARNING
# fcdl2bool iterates over all the files in the input folder and attemps to convert them into a bool file. 
# To convert an individual file, please use cdl2bool.
#
#
# EXAMPLES
# sh fcdl2bool ~/cdl_samples
# This command takes the "~/cdl_samples" folder as input and outputs the converted files into "~/cdl_samples". 
#
# sh fcdl2bool ~/cdl_samples ~/bool_samples
# This command takes the "~/cdl_samples" folder as input and outputs the converted files into "~/bool_samples". 

# VARIABBLES

# output folder
OUTPUT_FOLDER="~/"
OUTPUT_FILE=""
INPUT_FOLDER=""

# PROCESSING

# Argument processing
if [ $# -eq 1 ]
	then
	OUTPUT_FOLDER=$1
elif [ $# -eq 2 ]
	then
	OUTPUT_FOLDER=$2
else
	echo "Error in $0 - Invalid Argument Count"
    echo "Syntax: $0 <input_folder> [<output_folder>]"
   	exit
fi


# MAIN
clear
echo "Starting IML to BOOL conversion"

INPUT_FOLDER=${1%\/*}"/*"
for f in $INPUT_FOLDER
do
	FILE_NAME=$(basename "$f")
	OUTPUT_FILE=${OUTPUT_FOLDER%\/*}"/"${FILE_NAME%\.*}".bool" 
	sh cdl2bool.sh $f $OUTPUT_FILE
done