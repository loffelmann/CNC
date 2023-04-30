#!/bin/bash

cd java

javac -Xlint:unchecked -cp "./:../jSerialComm-2.9.1.jar:../gson-2.9.0.jar:../kabeja-0.4.jar" GilosController.java
jar cfm ../cnc.jar Manifest.txt {.,machines,filesources}/*.{class,java}
rm {.,machines,filesources}/*.class

cd ..

