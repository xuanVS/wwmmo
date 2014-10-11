#!/bin/bash
#
# Builds the .proto files and generates the required java code.
#

WIRECOMPILER=wire-compiler-1.5.3-SNAPSHOT-jar-with-dependencies.jar

java -jar $WIRECOMPILER --proto_path=. --java_out=../src *.proto

