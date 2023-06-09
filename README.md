# CNC

Software for CNC machines written from scratch (except for several libraries for communication
protocols and file formats). Good as an author's toy, less good for actual production.
Not all features are ready. Not all bugs are fixed.

Supports machines with up to 5 axes (the Java part is internally ready for 6 axes, but my
Arduino Nano does not have enough pins for that). The controller implements pul+dir
interface for five motor drivers and five inputs for axis homing sensors. Crash sensors
not implemented yet, but there is a software limitation of operating area.

Supported input formats are DXF (which gets translated to a tool path tracing outlines of the
image in a rather arbitrary order), and IDX (contains just raw binary arrays of coordinates
for individual axes). Dreaming about supporting g-code one day.

One intended application was a CNC Guilloche machine; That's why there are things named
"gilos" in the code.

Works with Java 17. Uses following libraries (not included in this repo):
* [Fazecast jSerialComm](http://fazecast.github.io/jSerialComm/) (serial port)
* [Kabeja](https://kabeja.sourceforge.net/) (DXF format)
* [Google GSON](https://github.com/google/gson) (JSON format)


# Contents

**java/**: A GUI program controlling the device over a serial port. Can be compiled with
*compile.sh* one dir up. GUI only in Czech now, sorry.

**Arduino/gilos/**: Firmware for Arduino Nano

**gilos.htm**: Example program for IDX data generation. Generates Guilloche-like curves.
GUI and JSON format in Czech...


# License

This is a toy project. It is far from fully debugged, and keeping things from harm was never the
main goal anyway. Please keep that in mind.

That being said, do whatever you please with my code.


![A test machine in operation](machine.jpg)

\* The machine is not included in this repo. Also, it was built by somebody else.

