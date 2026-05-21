#!/bin/bash
JAVA=/home/paolo/.vscode/extensions/redhat.java-1.54.0-linux-x64/jre/21.0.10-linux-x86_64/bin
mkdir -p out
$JAVA/javac -d out $(find src -name "*.java") && cp -r res/. out/ && $JAVA/java -cp out Main