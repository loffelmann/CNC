
#include "gilos.h"

const byte firmwareVersion = 7;



/// Settings ///////////////////////////////////////////////////////////////////////////////////////

// speed etc. constants

#define LOW_SPEED 32u
#define ZERO_SPEED_FAST 3200u
#define ZERO_SPEED_SLOW 320u
#define BACKLASH_SPEED 320u

#define SPEED_SHIFT 5

#define ZERO_STEP_DISTANCE 100
#define ZERO_BACK_DISTANCE 1000
#define ZERO_FORWARD_DISTANCE 1500

#define NO_LIMIT -2147483648L

// limit sensor settings

#define LIMIT_THRESHOLD 100

// pinout

#define STEP_X 4
#define STEP_Y 6
#define STEP_Z 8
#define STEP_U 10
#define STEP_V 12
#define STEP_W -1

#define DIR_X 3
#define DIR_Y 5
#define DIR_Z 7
#define DIR_U 9
#define DIR_V 11
#define DIR_W -1

#define LIMIT_X A4
#define LIMIT_Y A3
#define LIMIT_Z A2
#define LIMIT_U A1
#define LIMIT_V A0
#define LIMIT_W -1

#define CHUCK -1

#define SPINDLE_SPEED -1

#define ALARM 2



/// Misc //////////////////////////////////////////////////////////////////////////////////////////

int sign(int x){
  return (x<0)? -1
       : (x>0)?  1
       :         0;
}



/// State variables ////////////////////////////////////////////////////////////////////////////////

// axis settings

bool xAxisPresent = false;
bool yAxisPresent = false;
bool zAxisPresent = false;
bool uAxisPresent = false;
bool vAxisPresent = false;
bool wAxisPresent = false;

bool xZeroUp = false;
bool yZeroUp = false;
bool zZeroUp = false;
bool uZeroUp = false;
bool vZeroUp = false;
bool wZeroUp = false;

bool xStopAtLimit = false;
bool yStopAtLimit = false;
bool zStopAtLimit = false;
bool uStopAtLimit = false;
bool vStopAtLimit = false;
bool wStopAtLimit = false;

unsigned int xBacklash = 0;
unsigned int yBacklash = 0;
unsigned int zBacklash = 0;
unsigned int uBacklash = 0;
unsigned int vBacklash = 0;
unsigned int wBacklash = 0;
unsigned int maxBacklash = 0;

long xSetLow = NO_LIMIT;
long ySetLow = NO_LIMIT;
long zSetLow = NO_LIMIT;
long uSetLow = NO_LIMIT;
long vSetLow = NO_LIMIT;
long wSetLow = NO_LIMIT;

long xSetHigh = NO_LIMIT;
long ySetHigh = NO_LIMIT;
long zSetHigh = NO_LIMIT;
long uSetHigh = NO_LIMIT;
long vSetHigh = NO_LIMIT;
long wSetHigh = NO_LIMIT;

long xLow = NO_LIMIT;
long yLow = NO_LIMIT;
long zLow = NO_LIMIT;
long uLow = NO_LIMIT;
long vLow = NO_LIMIT;
long wLow = NO_LIMIT;

long xHigh = NO_LIMIT;
long yHigh = NO_LIMIT;
long zHigh = NO_LIMIT;
long uHigh = NO_LIMIT;
long vHigh = NO_LIMIT;
long wHigh = NO_LIMIT;


// tracking tool position
long xPos = 0;
long yPos = 0;
long zPos = 0;
long uPos = 0;
long vPos = 0;
long wPos = 0;
long limitedXPos = 0;
long limitedYPos = 0;
long limitedZPos = 0;
long limitedUPos = 0;
long limitedVPos = 0;
long limitedWPos = 0;
bool positionKnown = false;

// state of movement
unsigned int currentSpeed = LOW_SPEED;
bool moving = false;
bool stopping = false;
bool stopAtLimits = false;
bool ignoreBoundaries = false;

// state of current move
unsigned int bresenhamIter = 0;
unsigned int lastStepCount = 0;

// move buffer etc.
const unsigned int bufferLength = 64;
const unsigned int fullBufferLength = 60;
move_t buffer[bufferLength];
unsigned int currentMove = 0;
unsigned int stopMove = 0;
move_t priorityMove = {'N', 0, 0, 0, 0, 0, 0, 0};
bool doPriorityMove = false;

// state of serial protocol
serial_t serialPhase = S_READY;


// marking position tracking valid/invalid
void setPositionKnown(bool known){
  if(known){
    xLow = xSetLow;
    yLow = ySetLow;
    zLow = zSetLow;
    uLow = uSetLow;
    vLow = vSetLow;
    wLow = wSetLow;
    xHigh = xSetHigh;
    yHigh = ySetHigh;
    zHigh = zSetHigh;
    uHigh = uSetHigh;
    vHigh = vSetHigh;
    wHigh = wSetHigh;
  }
  else{
    xLow = NO_LIMIT;
    yLow = NO_LIMIT;
    zLow = NO_LIMIT;
    uLow = NO_LIMIT;
    vLow = NO_LIMIT;
    wLow = NO_LIMIT;
    xHigh = NO_LIMIT;
    yHigh = NO_LIMIT;
    zHigh = NO_LIMIT;
    uHigh = NO_LIMIT;
    vHigh = NO_LIMIT;
    wHigh = NO_LIMIT;
  }
  positionKnown = known;
}

// parsing axis config data (with protocol state variables)
char configAxis = 'X';
bool configPresent, configZeroUp, configStopAtLimit;
long configLow, configHigh;
int configBacklash;
void propagateConfig(){
  if(configAxis == 'X'){
    xAxisPresent = configPresent;
    xZeroUp = configZeroUp;
    xStopAtLimit = configStopAtLimit;
    xSetLow = configLow;
    xSetHigh = configHigh;
    xBacklash = configBacklash;
  }
  else if(configAxis == 'Y'){
    yAxisPresent = configPresent;
    yZeroUp = configZeroUp;
    yStopAtLimit = configStopAtLimit;
    ySetLow = configLow;
    ySetHigh = configHigh;
    yBacklash = configBacklash;
  }
  else if(configAxis == 'Z'){
    zAxisPresent = configPresent;
    zZeroUp = configZeroUp;
    zStopAtLimit = configStopAtLimit;
    zSetLow = configLow;
    zSetHigh = configHigh;
    zBacklash = configBacklash;
  }
  else if(configAxis == 'U'){
    uAxisPresent = configPresent;
    uZeroUp = configZeroUp;
    uStopAtLimit = configStopAtLimit;
    uSetLow = configLow;
    uSetHigh = configHigh;
    uBacklash = configBacklash;
  }
  else if(configAxis == 'V'){
    vAxisPresent = configPresent;
    vZeroUp = configZeroUp;
    vStopAtLimit = configStopAtLimit;
    vSetLow = configLow;
    vSetHigh = configHigh;
    vBacklash = configBacklash;
  }
  else if(configAxis == 'W'){
    wAxisPresent = configPresent;
    wZeroUp = configZeroUp;
    wStopAtLimit = configStopAtLimit;
    wSetLow = configLow;
    wSetHigh = configHigh;
    wBacklash = configBacklash;
  }
}


/// Communication //////////////////////////////////////////////////////////////////////////////////

move_t peekBuffer(){
  if(currentMove == stopMove)return {'M', 0, 0, 0, 0, 0, 0, 0};
  return buffer[currentMove];
}

void popBuffer(){
  if(currentMove == stopMove)return;
//  if((stopMove+1)%bufferLength == currentMove){
//    Serial.write(moving? 'F' : 'f'); // signal that buffer is free again - replaced by P/p
//  }
  Serial.write(moving? 'P' : 'p');
  currentMove = (currentMove+1) % bufferLength;
}

unsigned int remainingMoves(){
  return (stopMove+bufferLength-currentMove) % bufferLength;
}

void shortStatus(){
  bool bufferFull = remainingMoves() >= fullBufferLength;
  if(moving && !stopping)Serial.write(bufferFull? 'G' : 'F');
  else Serial.write(bufferFull? 'g' : 'f');
}

// helper for move data parsing
serial_t getNextMovePhase(serial_t currentPhase){
  switch(currentPhase){

    case S_READY: // move data start
      if(xAxisPresent) return S_MOVE_X0;
    case S_MOVE_X1:
      if(yAxisPresent) return S_MOVE_Y0;
    case S_MOVE_Y1:
      if(zAxisPresent) return S_MOVE_Z0;
    case S_MOVE_Z1:
      if(uAxisPresent) return S_MOVE_U0;
    case S_MOVE_U1:
      if(vAxisPresent) return S_MOVE_V0;
    case S_MOVE_V1:
      if(wAxisPresent) return S_MOVE_W0;
    case S_MOVE_W1:
      return S_MOVE_SPEED;

    case S_MOVE_IGNORE: // move data start, ignore
      if(xAxisPresent) return S_MOVE_IGNORE_X0;
    case S_MOVE_IGNORE_X1:
      if(yAxisPresent) return S_MOVE_IGNORE_Y0;
    case S_MOVE_IGNORE_Y1:
      if(zAxisPresent) return S_MOVE_IGNORE_Z0;
    case S_MOVE_IGNORE_Z1:
      if(uAxisPresent) return S_MOVE_IGNORE_U0;
    case S_MOVE_IGNORE_U1:
      if(vAxisPresent) return S_MOVE_IGNORE_V0;
    case S_MOVE_IGNORE_V1:
      if(wAxisPresent) return S_MOVE_IGNORE_W0;
    case S_MOVE_IGNORE_W1:
      return S_MOVE_IGNORE_SPEED;

    case S_MOVE_PRIO: // priority move data start
      if(xAxisPresent) return S_MOVE_PRIO_X0;
    case S_MOVE_PRIO_X1:
      if(yAxisPresent) return S_MOVE_PRIO_Y0;
    case S_MOVE_PRIO_Y1:
      if(zAxisPresent) return S_MOVE_PRIO_Z0;
    case S_MOVE_PRIO_Z1:
      if(uAxisPresent) return S_MOVE_PRIO_U0;
    case S_MOVE_PRIO_U1:
      if(vAxisPresent) return S_MOVE_PRIO_V0;
    case S_MOVE_PRIO_V1:
      if(wAxisPresent) return S_MOVE_PRIO_W0;
    case S_MOVE_PRIO_W1:
      return S_MOVE_PRIO_SPEED;

    default:
      return S_READY; // ?? should not occur
  }
}

// debugging
unsigned long minDuration = 1000000U;
unsigned long maxDuration = 0U;
unsigned long startTime, measuredTime;
void timeStart(){
  startTime = micros();
}
void timeStop(){
  measuredTime = micros() - startTime;
  if(measuredTime < minDuration)minDuration = measuredTime;
  if(measuredTime > maxDuration)maxDuration = measuredTime;
}
void timePrint(){
  Serial.print("M");
  Serial.print(maxDuration);
  Serial.print("m");
  Serial.println(minDuration);
  minDuration = 1000000U;
  maxDuration = 0U;
}

// top-level serial protocol implementation
void handleSerial(){
  if(Serial.available()){
    int data = Serial.read();
    switch(serialPhase){

      case S_READY:
        if(data == 'I'){ // identify
          Serial.print("gilos");
          delay(5);
        }
        else if(data == 'R'){ // reset
          stop();
          Serial.write('R');
          setPositionKnown(false);
          currentMove = stopMove = 0; // empty move buffer
          doPriorityMove = false;
          currentSpeed = LOW_SPEED;
          moving = false;
          stopAtLimits = false;
          bresenhamIter = 0;
#ifdef SUPPORT_SPINDLE_SPEED
          analogWrite(SPINDLE_SPEED, 0);
#endif
          delay(5);
        }
        else if(data == 's'){ // buffer status
          shortStatus();
        }
        else if(data == 'S'){ // full status
          Serial.write(firmwareVersion);
          Serial.write((byte)( // axis configuration 1
              (xAxisPresent << 0)
            | (yAxisPresent << 1)
            | (zAxisPresent << 2)
            | (uAxisPresent << 3)
            | (vAxisPresent << 4)
            | (wAxisPresent << 5)
          ));
          Serial.write((byte)( // axis configuration 2
              (xZeroUp << 0)
            | (yZeroUp << 1)
            | (zZeroUp << 2)
            | (uZeroUp << 3)
            | (vZeroUp << 4)
            | (wZeroUp << 5)
          ));
          Serial.write((byte)( // movement state
              (moving << 0)
            | (stopping << 1)
            | (stopAtLimits << 2)
#ifdef SUPPORT_CHUCK
            | (digitalRead(CHUCK) << 3)
#endif
          ));
          Serial.write((byte)(bresenhamIter >> 8)); // Bresenham state upper
          Serial.write((byte)(bresenhamIter & 0xFF)); // Bresenham state lower
          Serial.write(remainingMoves());
        }
        else if(data == '$'){ // Bresenham step count
          Serial.write('b');
          Serial.write((byte)((lastStepCount >> 8) & 0xFF));
          Serial.write((byte)((lastStepCount >> 0) & 0xFF));
          lastStepCount = 0;
        }
        else if(data == 'M'){ // move
          if((stopMove+1) % bufferLength == currentMove){
            serialPhase = getNextMovePhase(S_MOVE_IGNORE);
          }
          else{
            buffer[stopMove] = {(char)data, 0, 0, 0, 0, 0, 0, 0};
            serialPhase = getNextMovePhase(S_READY);
          }
        }
        else if(data == 'N' || data == 'K'){ // numerical move, keyboard move
          serialPhase = getNextMovePhase(S_MOVE_PRIO);
          priorityMove = {(char)data, 0, 0, 0, 0, 0, 0, 0};
        }
        else if(data == 'B'){ // begin movement
          if(remainingMoves() > 0){
            moving = true;
          }
          shortStatus();
        }
        else if(data == '.'){ // stop movement
          stop();
          doPriorityMove = false;
          shortStatus();
        }
        else if(data == '0'){ // go to zero position
          stop();

//          Serial.write('0'); // ?? debug dummified zeroing
//          xPos = yPos = zPos = uPos = vPos = wPos = 0;
//          limitedXPos = limitedYPos = limitedZPos = limitedUPos = limitedVPos = limitedWPos = 0;
//          setPositionKnown(true);

          setPositionKnown(false);
          serialPhase = S_ZERO;
        }
        else if(data == 'o'){ // open chuck
          chuckOpen();
        }
        else if(data == 'c'){ // close chuck
          chuckClose();
        }
        else if(data == 'r'){ // set spindle speed
          serialPhase = S_SPINDLE_SPEED;
        }
        else if(data == 'l'){ // blink led
          stop();
          for(int i=0; i<10; i++){
            delay(500);
            digitalWrite(LED_BUILTIN, !digitalRead(LED_BUILTIN));
          }
          Serial.write('l');
        }
        else if(data == 'L'){ // test limits
          stop();
          serialPhase = S_TEST_LIMITS;
          Serial.print("limit");
          delay(5);
        }
        else if(data == 'C'){ // configuration mode
          serialPhase = S_CONFIG;
        }
        else if(data == 'p'){ // position feedback
          if(xAxisPresent){
            Serial.write('x');
            Serial.write((byte)((xPos >> 24) & 0xFF));
            Serial.write((byte)((xPos >> 16) & 0xFF));
            Serial.write((byte)((xPos >>  8) & 0xFF));
            Serial.write((byte)((xPos >>  0) & 0xFF));
          }
          if(yAxisPresent){
            Serial.write('y');
            Serial.write((byte)((yPos >> 24) & 0xFF));
            Serial.write((byte)((yPos >> 16) & 0xFF));
            Serial.write((byte)((yPos >>  8) & 0xFF));
            Serial.write((byte)((yPos >>  0) & 0xFF));
          }
          if(zAxisPresent){
            Serial.write('z');
            Serial.write((byte)((zPos >> 24) & 0xFF));
            Serial.write((byte)((zPos >> 16) & 0xFF));
            Serial.write((byte)((zPos >>  8) & 0xFF));
            Serial.write((byte)((zPos >>  0) & 0xFF));
          }
          if(uAxisPresent){
            Serial.write('u');
            Serial.write((byte)((uPos >> 24) & 0xFF));
            Serial.write((byte)((uPos >> 16) & 0xFF));
            Serial.write((byte)((uPos >>  8) & 0xFF));
            Serial.write((byte)((uPos >>  0) & 0xFF));
          }
          if(vAxisPresent){
            Serial.write('v');
            Serial.write((byte)((vPos >> 24) & 0xFF));
            Serial.write((byte)((vPos >> 16) & 0xFF));
            Serial.write((byte)((vPos >>  8) & 0xFF));
            Serial.write((byte)((vPos >>  0) & 0xFF));
          }
          if(wAxisPresent){
            Serial.write('w');
            Serial.write((byte)((wPos >> 24) & 0xFF));
            Serial.write((byte)((wPos >> 16) & 0xFF));
            Serial.write((byte)((wPos >>  8) & 0xFF));
            Serial.write((byte)((wPos >>  0) & 0xFF));
          }
        }
        else if(data == 'P'){ // limited position feedback
          if(xAxisPresent){
            Serial.write('X');
            Serial.write((byte)((limitedXPos >> 24) & 0xFF));
            Serial.write((byte)((limitedXPos >> 16) & 0xFF));
            Serial.write((byte)((limitedXPos >>  8) & 0xFF));
            Serial.write((byte)((limitedXPos >>  0) & 0xFF));
          }
          if(yAxisPresent){
            Serial.write('Y');
            Serial.write((byte)((limitedYPos >> 24) & 0xFF));
            Serial.write((byte)((limitedYPos >> 16) & 0xFF));
            Serial.write((byte)((limitedYPos >>  8) & 0xFF));
            Serial.write((byte)((limitedYPos >>  0) & 0xFF));
          }
          if(zAxisPresent){
            Serial.write('Z');
            Serial.write((byte)((limitedZPos >> 24) & 0xFF));
            Serial.write((byte)((limitedZPos >> 16) & 0xFF));
            Serial.write((byte)((limitedZPos >>  8) & 0xFF));
            Serial.write((byte)((limitedZPos >>  0) & 0xFF));
          }
          if(uAxisPresent){
            Serial.write('U');
            Serial.write((byte)((limitedUPos >> 24) & 0xFF));
            Serial.write((byte)((limitedUPos >> 16) & 0xFF));
            Serial.write((byte)((limitedUPos >>  8) & 0xFF));
            Serial.write((byte)((limitedUPos >>  0) & 0xFF));
          }
          if(vAxisPresent){
            Serial.write('V');
            Serial.write((byte)((limitedVPos >> 24) & 0xFF));
            Serial.write((byte)((limitedVPos >> 16) & 0xFF));
            Serial.write((byte)((limitedVPos >>  8) & 0xFF));
            Serial.write((byte)((limitedVPos >>  0) & 0xFF));
          }
          if(wAxisPresent){
            Serial.write('W');
            Serial.write((byte)((limitedWPos >> 24) & 0xFF));
            Serial.write((byte)((limitedWPos >> 16) & 0xFF));
            Serial.write((byte)((limitedWPos >>  8) & 0xFF));
            Serial.write((byte)((limitedWPos >>  0) & 0xFF));
          }
        }
        else{
          Serial.write('?'); // unrecognized command
        }
        break;

      // parsing move data
      case S_MOVE_X0:
        buffer[stopMove].dx |= data << 8;
        serialPhase = S_MOVE_X1;
        break;
      case S_MOVE_X1:
        buffer[stopMove].dx |= data;
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_Y0:
        buffer[stopMove].dy |= data << 8;
        serialPhase = S_MOVE_Y1;
        break;
      case S_MOVE_Y1:
        buffer[stopMove].dy |= data;
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_Z0:
        buffer[stopMove].dz |= data << 8;
        serialPhase = S_MOVE_Z1;
        break;
      case S_MOVE_Z1:
        buffer[stopMove].dz |= data;
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_U0:
        buffer[stopMove].du |= data << 8;
        serialPhase = S_MOVE_U1;
        break;
      case S_MOVE_U1:
        buffer[stopMove].du |= data;
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_V0:
        buffer[stopMove].dv |= data << 8;
        serialPhase = S_MOVE_V1;
        break;
      case S_MOVE_V1:
        buffer[stopMove].dv |= data;
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_W0:
        buffer[stopMove].dw |= data << 8;
        serialPhase = S_MOVE_W1;
        break;
      case S_MOVE_W1:
        buffer[stopMove].dw |= data;
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_SPEED:
        buffer[stopMove].endSpeed = data;
        stopMove = (stopMove+1) % bufferLength;
        shortStatus();
        serialPhase = S_READY;
        break;

      // ignoring ignored-move data
      case S_MOVE_IGNORE_X0:
        serialPhase = S_MOVE_IGNORE_X1;
        break;
      case S_MOVE_IGNORE_X1:
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_IGNORE_Y0:
        serialPhase = S_MOVE_IGNORE_Y1;
        break;
      case S_MOVE_IGNORE_Y1:
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_IGNORE_Z0:
        serialPhase = S_MOVE_IGNORE_Z1;
        break;
      case S_MOVE_IGNORE_Z1:
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_IGNORE_U0:
        serialPhase = S_MOVE_IGNORE_U1;
        break;
      case S_MOVE_IGNORE_U1:
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_IGNORE_V0:
        serialPhase = S_MOVE_IGNORE_V1;
        break;
      case S_MOVE_IGNORE_V1:
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_IGNORE_W0:
        serialPhase = S_MOVE_IGNORE_W1;
        break;
      case S_MOVE_IGNORE_W1:
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_IGNORE_SPEED:
        Serial.write(moving? 'O' : 'o'); // buffer overflow
        serialPhase = S_READY;
        break;

      // parsing priority (manual) move data
      case S_MOVE_PRIO_X0:
        priorityMove.dx |= data << 8;
        serialPhase = S_MOVE_PRIO_X1;
        break;
      case S_MOVE_PRIO_X1:
        priorityMove.dx |= data;
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_PRIO_Y0:
        priorityMove.dy |= data << 8;
        serialPhase = S_MOVE_PRIO_Y1;
        break;
      case S_MOVE_PRIO_Y1:
        priorityMove.dy |= data;
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_PRIO_Z0:
        priorityMove.dz |= data << 8;
        serialPhase = S_MOVE_PRIO_Z1;
        break;
      case S_MOVE_PRIO_Z1:
        priorityMove.dz |= data;
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_PRIO_U0:
        priorityMove.du |= data << 8;
        serialPhase = S_MOVE_PRIO_U1;
        break;
      case S_MOVE_PRIO_U1:
        priorityMove.du |= data;
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_PRIO_V0:
        priorityMove.dv |= data << 8;
        serialPhase = S_MOVE_PRIO_V1;
        break;
      case S_MOVE_PRIO_V1:
        priorityMove.dv |= data;
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_PRIO_W0:
        priorityMove.dw |= data << 8;
        serialPhase = S_MOVE_PRIO_W1;
        break;
      case S_MOVE_PRIO_W1:
        priorityMove.dw |= data;
        serialPhase = getNextMovePhase(serialPhase);
        break;
      case S_MOVE_PRIO_SPEED:
        priorityMove.endSpeed = data;
        doPriorityMove = true;
        serialPhase = S_READY;
        Serial.write('G');
        break;

      case S_SPINDLE_SPEED:
        setSpindleSpeed(data);
        serialPhase = S_READY;
        break;

      case S_ZERO: // axis homing
        if(data == 'R' || data == '.'){ // reset
          stop();
          serialPhase = S_READY;
        }
        break;

      case S_TEST_LIMITS:
        if(data == 'R' || data == '.'){ // reset
          digitalWrite(LED_BUILTIN, LOW);
          serialPhase = S_READY;
        }
        break;

      // parsing axis config data
      case S_CONFIG:
        configLow = 0;
        configHigh = 0;
        configBacklash = 0;
             if(data == 'X'){ configAxis='X'; serialPhase=S_CONFIG_FLAGS; }
        else if(data == 'Y'){ configAxis='Y'; serialPhase=S_CONFIG_FLAGS; }
        else if(data == 'Z'){ configAxis='Z'; serialPhase=S_CONFIG_FLAGS; }
        else if(data == 'U'){ configAxis='U'; serialPhase=S_CONFIG_FLAGS; }
        else if(data == 'V'){ configAxis='V'; serialPhase=S_CONFIG_FLAGS; }
        else if(data == 'W'){ configAxis='W'; serialPhase=S_CONFIG_FLAGS; }
        else if(data == 'R' || data == '.'){ serialPhase=S_READY; }
        break;
      case S_CONFIG_FLAGS:
             if(data == 'd'){ configPresent=true;  configZeroUp=false; configStopAtLimit=false; }
        else if(data == 'u'){ configPresent=true;  configZeroUp=true;  configStopAtLimit=false; }
        else if(data == 'D'){ configPresent=true;  configZeroUp=false; configStopAtLimit=true; }
        else if(data == 'U'){ configPresent=true;  configZeroUp=true;  configStopAtLimit=true; }
        else if(data == '0'){ configPresent=false; configZeroUp=false; configStopAtLimit=false; }
        serialPhase = S_CONFIG_LOW0;
        break;
      case S_CONFIG_LOW0:
        configLow |= (long)data << 24L;
        serialPhase = S_CONFIG_LOW1;
        break;
      case S_CONFIG_LOW1:
        configLow |= (long)data << 16L;
        serialPhase = S_CONFIG_LOW2;
        break;
      case S_CONFIG_LOW2:
        configLow |= (long)data << 8L;
        serialPhase = S_CONFIG_LOW3;
        break;
      case S_CONFIG_LOW3:
        configLow |= (long)data;
        serialPhase = S_CONFIG_HIGH0;
        break;
      case S_CONFIG_HIGH0:
        configHigh |= (long)data << 24L;
        serialPhase = S_CONFIG_HIGH1;
        break;
      case S_CONFIG_HIGH1:
        configHigh |= (long)data << 16L;
        serialPhase = S_CONFIG_HIGH2;
        break;
      case S_CONFIG_HIGH2:
        configHigh |= (long)data << 8L;
        serialPhase = S_CONFIG_HIGH3;
        break;
      case S_CONFIG_HIGH3:
        configHigh |= (long)data;
        serialPhase = S_CONFIG_BACKLASH0;
        break;
      case S_CONFIG_BACKLASH0:
        configBacklash |= data << 8;
        serialPhase = S_CONFIG_BACKLASH1;
        break;
      case S_CONFIG_BACKLASH1:
        configBacklash |= data;
        propagateConfig();
        setPositionKnown(positionKnown); // propagate xSetLow to xLow if necessary
        Serial.write('c');
        serialPhase = S_CONFIG;
        break;

    }
  }
}



/// Driving motors /////////////////////////////////////////////////////////////////////////////////

// checking sensors for axis homing
byte readLimits(){
  byte limit = 0;

//  if(analogRead(LIMIT_X) >= LIMIT_THRESHOLD)limit |= 0b000001;
//  if(analogRead(LIMIT_Y) >= LIMIT_THRESHOLD)limit |= 0b000010;
//  if(analogRead(LIMIT_Z) >= LIMIT_THRESHOLD)limit |= 0b000100;
//  if(analogRead(LIMIT_U) >= LIMIT_THRESHOLD)limit |= 0b001000;
//  if(analogRead(LIMIT_V) >= LIMIT_THRESHOLD)limit |= 0b010000;
//#ifdef SUPPORT_W
//  if(analogRead(LIMIT_W) >= LIMIT_THRESHOLD)limit |= 0b100000;
//#endif

  digitalRead(LIMIT_X);
  if(digitalRead(LIMIT_X))limit |= 0b000001;
  digitalRead(LIMIT_Y);
  if(digitalRead(LIMIT_Y))limit |= 0b000010;
  digitalRead(LIMIT_Z);
  if(digitalRead(LIMIT_Z))limit |= 0b000100;
  digitalRead(LIMIT_U);
  if(digitalRead(LIMIT_U))limit |= 0b001000;
  digitalRead(LIMIT_V);
  if(digitalRead(LIMIT_V))limit |= 0b010000;
#ifdef SUPPORT_W
  digitalRead(LIMIT_W);
  if(digitalRead(LIMIT_W))limit |= 0b100000;
#endif

  return limit;
}

// tracking movement direction for backlash correction
int directionX = 0;
int directionY = 0;
int directionZ = 0;
int directionU = 0;
int directionV = 0;
int directionW = 0;

// do a single step with selected motor
bool step(int x, int y, int z, int u, int v, int w){

  if(stopAtLimits){
    byte limits = readLimits();
    if(limits & 0b000001)x = 0;
    if(limits & 0b000010)y = 0;
    if(limits & 0b000100)z = 0;
    if(limits & 0b001000)u = 0;
    if(limits & 0b010000)v = 0;
    if(limits & 0b100000)w = 0;
  }

  bool moved = (x!=0) || (y!=0) || (z!=0) || (u!=0) || (v!=0) || (w!=0);

  // updating projected position
  xPos += x;
  yPos += y;
  zPos += z;
  uPos += u;
  vPos += v;
  wPos += w;

  if(positionKnown && !ignoreBoundaries){
    if((xLow!=NO_LIMIT && xPos<=xLow && xPos<=limitedXPos) || (xHigh!=NO_LIMIT && xPos>=xHigh && xPos>=limitedXPos))x = 0;
    if((yLow!=NO_LIMIT && yPos<=yLow && yPos<=limitedYPos) || (yHigh!=NO_LIMIT && yPos>=yHigh && yPos>=limitedYPos))y = 0;
    if((zLow!=NO_LIMIT && zPos<=zLow && zPos<=limitedZPos) || (zHigh!=NO_LIMIT && zPos>=zHigh && zPos>=limitedZPos))z = 0;
    if((uLow!=NO_LIMIT && uPos<=uLow && uPos<=limitedUPos) || (uHigh!=NO_LIMIT && uPos>=uHigh && uPos>=limitedUPos))u = 0;
    if((vLow!=NO_LIMIT && vPos<=vLow && vPos<=limitedVPos) || (vHigh!=NO_LIMIT && vPos>=vHigh && vPos>=limitedVPos))v = 0;
    if((wLow!=NO_LIMIT && wPos<=wLow && wPos<=limitedWPos) || (wHigh!=NO_LIMIT && wPos>=wHigh && wPos>=limitedWPos))w = 0;
  }

  // changing direction
  bool changeX = (x != 0) && (directionX != sign(x));
  bool changeY = (y != 0) && (directionY != sign(y));
  bool changeZ = (z != 0) && (directionZ != sign(z));
  bool changeU = (u != 0) && (directionU != sign(u));
  bool changeV = (v != 0) && (directionV != sign(v));
  bool changeW = (w != 0) && (directionW != sign(w));
  if(changeX || changeY || changeZ || changeU || changeV || changeW){
    if(changeX)directionX = sign(x);
    if(changeY)directionY = sign(y);
    if(changeZ)directionZ = sign(z);
    if(changeU)directionU = sign(u);
    if(changeV)directionV = sign(v);
    if(changeW)directionW = sign(w);
    digitalWrite(DIR_X, (directionX==-1)? LOW : HIGH);
    digitalWrite(DIR_Y, (directionY==-1)? LOW : HIGH);
    digitalWrite(DIR_Z, (directionZ==-1)? LOW : HIGH);
    digitalWrite(DIR_U, (directionU==-1)? LOW : HIGH);
    digitalWrite(DIR_V, (directionV==-1)? LOW : HIGH);
#ifdef SUPPORT_W
    digitalWrite(DIR_W, (directionW==-1)? LOW : HIGH);
#endif
    delayMicroseconds(20);
    for(unsigned int i=0; i<maxBacklash; i++){
      digitalWrite(STEP_X, (changeX && i<xBacklash)? HIGH : LOW);
      digitalWrite(STEP_Y, (changeY && i<yBacklash)? HIGH : LOW);
      digitalWrite(STEP_Z, (changeZ && i<zBacklash)? HIGH : LOW);
      digitalWrite(STEP_U, (changeU && i<uBacklash)? HIGH : LOW);
      digitalWrite(STEP_V, (changeV && i<vBacklash)? HIGH : LOW);
#ifdef SUPPORT_W
      digitalWrite(STEP_W, (changeW && i<wBacklash)? HIGH : LOW);
#endif
      delayMicroseconds(20);
      digitalWrite(STEP_X, LOW);
      digitalWrite(STEP_Y, LOW);
      digitalWrite(STEP_Z, LOW);
      digitalWrite(STEP_U, LOW);
      digitalWrite(STEP_V, LOW);
#ifdef SUPPORT_W
      digitalWrite(STEP_W, LOW);
#endif
      unsigned long remainingTime = 1000000L / BACKLASH_SPEED;
      if(remainingTime > 65535){
        delay(remainingTime/1000);
      }
      else if(remainingTime > 0){
        delayMicroseconds(remainingTime);
      }
    }
  }

  // starting a pulse
  digitalWrite(STEP_X, x? HIGH : LOW);
  digitalWrite(STEP_Y, y? HIGH : LOW);
  digitalWrite(STEP_Z, z? HIGH : LOW);
  digitalWrite(STEP_U, u? HIGH : LOW);
  digitalWrite(STEP_V, v? HIGH : LOW);
#ifdef SUPPORT_W
  digitalWrite(STEP_W, w? HIGH : LOW);
#endif
//  delayMicroseconds(20);

  // updating limited position
  limitedXPos += x;
  limitedYPos += y;
  limitedZPos += z;
  limitedUPos += u;
  limitedVPos += v;
  limitedWPos += w;

  // loading next data
  handleSerial();

  // ending the pulse
  digitalWrite(STEP_X, LOW);
  digitalWrite(STEP_Y, LOW);
  digitalWrite(STEP_Z, LOW);
  digitalWrite(STEP_U, LOW);
  digitalWrite(STEP_V, LOW);
#ifdef SUPPORT_W
  digitalWrite(STEP_W, LOW);
#endif
//  delayMicroseconds(20);

  return moved;
}



/// Movements //////////////////////////////////////////////////////////////////////////////////////

void stop(){
  stopping = true;
}

// low-level translation of line data to motor steps
void bresenham(
  unsigned int dx, unsigned int dy, unsigned int dz, unsigned int du, unsigned int dv, unsigned int dw,
  char dirX, char dirY, char dirZ, char dirU, char dirV, char dirW,
  byte rot,
  unsigned int startSpeed, unsigned int endSpeed // steps/s
){

  int diffY = 2*dy - dx;
  int diffZ = 2*dz - dx;
  int diffU = 2*du - dx;
  int diffV = 2*dv - dx;
  int diffW = 2*dw - dx;
  int y = 1;
  int z = 0;
  int u = 0;
  int v = 0;
  int w = 0;
  bool movement;

  float accel = (((float)endSpeed)*endSpeed-((float)startSpeed)*startSpeed)/(2*dx); // steps/s/s
  float speed = startSpeed; // steps/s
  float waitTime;
  unsigned long startTime;
  unsigned long currentTime;
  long remainingTime;
  bool firstIter = true;

  lastStepCount = 0;
  for(; bresenhamIter<dx; ++bresenhamIter){
    startTime = micros();
    if(stopping)return;

    if(rot == 0){
      movement = step(dirX, dirY*y, dirZ*z, dirU*u, dirV*v, dirW*w);
    }
    else if(rot == 1){
      movement = step(dirW*w, dirX, dirY*y, dirZ*z, dirU*u, dirV*v);
    }
    else if(rot == 2){
      movement = step(dirV*v, dirW*w, dirX, dirY*y, dirZ*z, dirU*u);
    }
    else if(rot == 3){
      movement = step(dirU*u, dirV*v, dirW*w, dirX, dirY*y, dirZ*z);
    }
    else if(rot == 4){
      movement = step(dirZ*z, dirU*u, dirV*v, dirW*w, dirX, dirY*y);
    }
    else{
      movement = step(dirY*y, dirZ*z, dirU*u, dirV*v, dirW*w, dirX);
    }

    if(movement)lastStepCount++;
    else if(stopAtLimits && !firstIter)break;

    if(diffY > 0){
      y = 1;
      diffY -= 2*dx;
    }
    else y = 0;
    diffY += 2*dy;
    if(diffZ > 0){
      z = 1;
      diffZ -= 2*dx;
    }
    else z = 0;
    diffZ += 2*dz;
    if(diffU > 0){
      u = 1;
      diffU -= 2*dx;
    }
    else u = 0;
    diffU += 2*du;
    if(diffV > 0){
      v = 1;
      diffV -= 2*dx;
    }
    else v = 0;
    diffV += 2*dv;
    if(diffW > 0){
      w = 1;
      diffW -= 2*dx;
    }
    else w = 0;
    diffW += 2*dw;

    firstIter = false;

    if(startSpeed == endSpeed){
      remainingTime = 1000000L / startSpeed;
    }
    else{
      waitTime = 2*(sqrt(speed*speed+accel)-speed)/accel;
      speed += waitTime*accel;
      remainingTime = (long)max(waitTime*1e6, 0);
    }
    currentTime = micros();
    remainingTime -= (currentTime>startTime)? (currentTime-startTime) : (currentTime+(0xFFFFFFFFL-startTime));
    if(remainingTime > 65535){
      delay(remainingTime/1000);
    }
    else if(remainingTime > 0){
      delayMicroseconds(remainingTime);
    }
//    digitalWrite(LED_BUILTIN, (remainingTime>0)? HIGH : LOW); // debug, measuring maximum step rate
  }
  bresenhamIter = 0;
}

// higher-level drawing of arbitrary line segments
void lineBy(
  int dx, int dy, int dz, int du, int dv, int dw,
  unsigned int endSpeed
){
  if(!xAxisPresent)dx = 0;
  if(!yAxisPresent)dy = 0;
  if(!zAxisPresent)dz = 0;
  if(!uAxisPresent)du = 0;
  if(!vAxisPresent)dv = 0;
  if(!wAxisPresent)dw = 0;

  unsigned int absDx = abs(dx);
  unsigned int absDy = abs(dy);
  unsigned int absDz = abs(dz);
  unsigned int absDu = abs(du);
  unsigned int absDv = abs(dv);
  unsigned int absDw = abs(dw);

  unsigned int maxD = absDx;
  if(absDy > maxD)maxD = absDy;
  if(absDz > maxD)maxD = absDz;
  if(absDu > maxD)maxD = absDu;
  if(absDv > maxD)maxD = absDv;
  if(absDw > maxD)maxD = absDw;

  if(maxD == absDx){
    bresenham(absDx,    absDy,    absDz,    absDu,    absDv,    absDw,
              sign(dx), sign(dy), sign(dz), sign(du), sign(dv), sign(dw),
              0, currentSpeed, endSpeed);
  }
  else if(maxD == absDy){
    bresenham(absDy,    absDz,    absDu,    absDv,    absDw,    absDx,
              sign(dy), sign(dz), sign(du), sign(dv), sign(dw), sign(dx),
              1, currentSpeed, endSpeed);
  }
  else if(maxD == absDz){
    bresenham(absDz,    absDu,    absDv,    absDw,    absDx,    absDy,
              sign(dz), sign(du), sign(dv), sign(dw), sign(dx), sign(dy),
              2, currentSpeed, endSpeed);
  }
  else if(maxD == absDu){
    bresenham(absDu,    absDv,    absDw,    absDx,    absDy,    absDz,
              sign(du), sign(dv), sign(dw), sign(dx), sign(dy), sign(dz),
              3, currentSpeed, endSpeed);
  }
  else if(maxD == absDv){
    bresenham(absDv,    absDw,    absDx,    absDy,    absDz,    absDu,
              sign(dv), sign(dw), sign(dx), sign(dy), sign(dz), sign(du),
              4, currentSpeed, endSpeed);
  }
  else{
    bresenham(absDw,    absDx,    absDy,    absDz,    absDu,    absDv,
              sign(dw), sign(dx), sign(dy), sign(dz), sign(du), sign(dv),
              5, currentSpeed, endSpeed);
  }
  currentSpeed = endSpeed;
}

// debug axis homing
void printDebugMovingStatus(){
  Serial.write(moving? (stopping? 'S' : 'R') : (stopping? 's' : 'r'));
}
void printDebugLimitStatus(){
  delay(100);
  byte limits = readLimits();
  Serial.print("dL");
  Serial.write((byte)(64
    | ((!xAxisPresent || (limits & 0b000001)) << 0)
    | ((!yAxisPresent || (limits & 0b000010)) << 1)
    | ((!zAxisPresent || (limits & 0b000100)) << 2)
    | ((!uAxisPresent || (limits & 0b001000)) << 3)
    | ((!vAxisPresent || (limits & 0b010000)) << 4)
    | ((!wAxisPresent || (limits & 0b100000)) << 5)
  ));
}
void printDebugStepCount(){
  delay(100);
  Serial.write('D');
  Serial.write((byte)(64 | ((lastStepCount >> 12) & 15)));
  Serial.write((byte)(64 | ((lastStepCount >>  8) & 15)));
  delay(100);
  Serial.write('d');
  Serial.write((byte)(64 | ((lastStepCount >>  4) & 15)));
  Serial.write((byte)(64 | ((lastStepCount >>  0) & 15)));
}

// axis homing
bool goTowardZero(){
  byte limits = readLimits();
  if(   (!xAxisPresent || (limits & 0b000001))
     && (!yAxisPresent || (limits & 0b000010))
     && (!zAxisPresent || (limits & 0b000100))
     && (!uAxisPresent || (limits & 0b001000))
     && (!vAxisPresent || (limits & 0b010000))
     && (!wAxisPresent || (limits & 0b100000))
  ){ // all limits reached
//    Serial.print("d1"); printDebugMovingStatus();
//    printDebugLimitStatus();
    stopAtLimits = false;
    delay(500);
//    Serial.print("d2"); printDebugMovingStatus();
    ignoreBoundaries = true;
    lineBy(
      xZeroUp? -ZERO_BACK_DISTANCE : ZERO_BACK_DISTANCE,
      yZeroUp? -ZERO_BACK_DISTANCE : ZERO_BACK_DISTANCE,
      zZeroUp? -ZERO_BACK_DISTANCE : ZERO_BACK_DISTANCE,
      uZeroUp? -ZERO_BACK_DISTANCE : ZERO_BACK_DISTANCE,
      vZeroUp? -ZERO_BACK_DISTANCE : ZERO_BACK_DISTANCE,
      wZeroUp? -ZERO_BACK_DISTANCE : ZERO_BACK_DISTANCE,
      ZERO_SPEED_FAST
    );
    ignoreBoundaries = false;
//    Serial.print("d3"); printDebugMovingStatus();
//    printDebugLimitStatus();
//    printDebugStepCount();
    stopAtLimits = true;
    delay(500);
//    Serial.print("d4"); printDebugMovingStatus();
    currentSpeed = ZERO_SPEED_SLOW;
    ignoreBoundaries = true;
    lineBy(
      xZeroUp? ZERO_FORWARD_DISTANCE : -ZERO_FORWARD_DISTANCE,
      yZeroUp? ZERO_FORWARD_DISTANCE : -ZERO_FORWARD_DISTANCE,
      zZeroUp? ZERO_FORWARD_DISTANCE : -ZERO_FORWARD_DISTANCE,
      uZeroUp? ZERO_FORWARD_DISTANCE : -ZERO_FORWARD_DISTANCE,
      vZeroUp? ZERO_FORWARD_DISTANCE : -ZERO_FORWARD_DISTANCE,
      wZeroUp? ZERO_FORWARD_DISTANCE : -ZERO_FORWARD_DISTANCE,
      ZERO_SPEED_SLOW
    );
    ignoreBoundaries = false;
    stopAtLimits = false;
//    Serial.print("d5"); printDebugMovingStatus();
//    printDebugLimitStatus();
//    printDebugStepCount();
    return true; // zero reached
  }
  else{
//    Serial.print("dz"); printDebugMovingStatus();
//    printDebugLimitStatus();
    stopAtLimits = true;
    ignoreBoundaries = true;
    lineBy(
      xZeroUp? ZERO_STEP_DISTANCE : -ZERO_STEP_DISTANCE,
      yZeroUp? ZERO_STEP_DISTANCE : -ZERO_STEP_DISTANCE,
      zZeroUp? ZERO_STEP_DISTANCE : -ZERO_STEP_DISTANCE,
      uZeroUp? ZERO_STEP_DISTANCE : -ZERO_STEP_DISTANCE,
      vZeroUp? ZERO_STEP_DISTANCE : -ZERO_STEP_DISTANCE,
      wZeroUp? ZERO_STEP_DISTANCE : -ZERO_STEP_DISTANCE,
      ZERO_SPEED_FAST
    );
    ignoreBoundaries = false;
    stopAtLimits = false;
//    Serial.print("dZ"); printDebugMovingStatus();
//    printDebugLimitStatus();
//    printDebugStepCount();
    return false; // not there yet
  }
}

void chuckOpen(){
#ifdef SUPPORT_CHUCK
  digitalWrite(CHUCK, HIGH);
#endif
}

void chuckClose(){
#ifdef SUPPORT_CHUCK
  digitalWrite(CHUCK, LOW);
#endif
}

void setSpindleSpeed(int pwm){
#ifdef SUPPORT_SPINDLE_SPEED
  analogWrite(SPINDLE_SPEED, pwm & 0xFF);
#endif
}



////////////////////////////////////////////////////////////////////////////////////////////////////

void setup(){
  pinMode(LED_BUILTIN, OUTPUT);

  pinMode(STEP_X, OUTPUT);
  pinMode(STEP_Y, OUTPUT);
  pinMode(STEP_Z, OUTPUT);
  pinMode(STEP_U, OUTPUT);
  pinMode(STEP_V, OUTPUT);
#ifdef SUPPORT_W
  pinMode(STEP_W, OUTPUT);
#endif

  pinMode(DIR_X, OUTPUT);
  pinMode(DIR_Y, OUTPUT);
  pinMode(DIR_Z, OUTPUT);
  pinMode(DIR_U, OUTPUT);
  pinMode(DIR_V, OUTPUT);
#ifdef SUPPORT_W
  pinMode(DIR_W, OUTPUT);
#endif

  pinMode(LIMIT_X, INPUT);
  pinMode(LIMIT_Y, INPUT);
  pinMode(LIMIT_Z, INPUT);
  pinMode(LIMIT_U, INPUT);
  pinMode(LIMIT_V, INPUT);
#ifdef SUPPORT_W
  pinMode(LIMIT_W, INPUT);
#endif

#ifdef SUPPORT_CHUCK
  pinMode(CHUCK, OUTPUT);
  digitalWrite(CHUCK, LOW);
#endif

#ifdef SUPPORT_SPINDLE_SPEED
  pinMode(SPINDLE_SPEED, OUTPUT);
  analogWrite(SPINDLE_SPEED, 0);
#endif

  digitalWrite(LED_BUILTIN, LOW);

  digitalWrite(STEP_X, LOW);
  digitalWrite(STEP_Y, LOW);
  digitalWrite(STEP_Z, LOW);
  digitalWrite(STEP_U, LOW);
  digitalWrite(STEP_V, LOW);
#ifdef SUPPORT_W
  digitalWrite(STEP_W, LOW);
#endif

  digitalWrite(DIR_X, LOW);
  digitalWrite(DIR_Y, LOW);
  digitalWrite(DIR_Z, LOW);
  digitalWrite(DIR_U, LOW);
  digitalWrite(DIR_V, LOW);
#ifdef SUPPORT_W
  digitalWrite(DIR_W, LOW);
#endif

  Serial.begin(115200);
  while(!Serial)delay(1);
  Serial.write('R');

  delay(10);
}

void loop(){
  if(stopping){
    moving = false;
    stopping = false;
    currentSpeed = LOW_SPEED;
    Serial.write('.');
    return;
  }
  else if(serialPhase == S_ZERO){ // axis homing
    if(goTowardZero()){
      serialPhase = S_READY;
      xPos = yPos = zPos = uPos = vPos = wPos = 0;
      limitedXPos = limitedYPos = limitedZPos = limitedUPos = limitedVPos = limitedWPos = 0;
      setPositionKnown(true);
      Serial.write('0');
    }
    return;
  }
  if(serialPhase == S_TEST_LIMITS){ // testing homing sensors with LED
    digitalWrite(LED_BUILTIN, readLimits()? HIGH : LOW);
  }

  // doing a move

  unsigned int remaining = remainingMoves();

  if(doPriorityMove){
    doPriorityMove = false;
    unsigned int origBresenhamIter = bresenhamIter;
    bresenhamIter = 0;
    currentSpeed = ((unsigned int)priorityMove.endSpeed) << SPEED_SHIFT;
    Serial.write('W');
    ignoreBoundaries = (priorityMove.type == 'K');
    lineBy(
      priorityMove.dx, priorityMove.dy, priorityMove.dz, priorityMove.du, priorityMove.dv, priorityMove.dw,
      currentSpeed
    );
    ignoreBoundaries = false;
    Serial.write(stopping? 'q' : 'Q');
    bresenhamIter = origBresenhamIter;
    if(stopping)doPriorityMove = false;
    if(!doPriorityMove)shortStatus();
  }

  else if(moving && remaining){
    move_t nextMove = peekBuffer();
    lineBy(
      nextMove.dx, nextMove.dy, nextMove.dz, nextMove.du, nextMove.dv, nextMove.dw,
      (remaining == 1)? LOW_SPEED : max(((unsigned int)nextMove.endSpeed) << SPEED_SHIFT, LOW_SPEED)
    );
    if(!stopping)popBuffer();
  }

  else{
    if(moving && !remaining){
      moving = false;
      shortStatus();
    }
    if(!moving)currentSpeed = LOW_SPEED;
    handleSerial();
    delay(1);
  }

}
