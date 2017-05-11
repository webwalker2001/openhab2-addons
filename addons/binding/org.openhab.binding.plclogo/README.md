# PLCLogo Binding

This binding provides native support of Siemens LOGO! PLC devices. Communication with Logo is done via Moka7 library.
Currently only two devices are supported: 0BA7 (LOGO! 7) and 0BA8 (LOGO! 8). Additionally multiple devices are supported.
Different families of LOGO! devices should work also, but was not tested now due to lack of hardware.
Binding works nicely at least 100ms polling rate, if network connection is stable.

## Discovery

Siemens LOGO! devices can be manually discovered by sending a request to every IP on the network.
This functionality should be used with caution, because it produces heavy load to the operating hardware.
For this reason, the binding does not do an automatic background discovery, but discovery can be triggered manually.

## Bridge configuration

Every Siemens LOGO! PLC is configured as bridge:

```
Bridge plclogo:device:<DeviceId> [ address="<ip>", family="<0BA7/0BA8>", localTSAP="0x<number>", remoteTSAP="0x<number>", refresh=<number> ]
```

| Parameter  | Type    | Required   | Default   | Description                                                      |
| ---------- | :-----: | :--------: | :-------: | ---------------------------------------------------------------- |
| address    | String  | Yes        |           | IP address of the LOGO! PLC.                                     |
| family     | String  | Yes        |           | LOGO! family to communicate with. Can be `0BA7` or `0BA8` now.   |
| localTSAP  | String  | Yes        |           | TSAP (as hex) is used by the local instance. Check configuration |
|            |         |            |           | in LOGO!Soft Comfort. Common used value is `0x0300`.             |
| remoteTSAP | String  | Yes        |           | TSAP (as hex) of the remote LOGO! PLC, as configured by          |
|            |         |            |           | LOGO!Soft Comfort. Common used value is `0x0200`.                |
| refresh    | Integer | No         | 100ms     | Polling interval, in milliseconds. Is used for query the LOGO!.  |

Be sure not to use the same values for localTSAP and remoteTSAP, if configure more than one LOGO!

## Thing configuration

Binding supports two types of things: digital and analog.

### Digital Things
The configuration pattern for digital things is as follow

```
Thing plclogo:digital:<ThingId> [ block="<name>", force=<true/false> ]
```

| Parameter | Type    | Required   | Default   | Description                                                  |
| --------- | :-----: | :--------: | :-------: | ------------------------------------------------------------ |
| block     | String  | Yes        |           | Block name                                                   |
| force     | Boolean | No         | false     | Send current value to openHAB, independent if changed or not |

Follow block names are allowed for digital things:

| Type           | `0BA7`              | `0BA8`            | 
| -------------- | :-----------------: | :---------------: |
| Input          | `I[1-24]`           | `I[1-24]`         |
| Output         | `Q[1-16]`           | `Q[1-20]`         |
| Marker         | `M[1-27]`           | `M[1-64]`         |
| Network input  |                     | `NI[1-64]`        |
| Network output |                     | `NQ[1-64]`        |
| Memory         | `VB[0-850].[0-7]`   | `VB[0-850].[0-7]` |

### Analog Things
The configuration pattern for analog things is as follow

```
Thing plclogo:analog:<ThingId> [ block="<name>", threshold=<number>, force=<true/false>, type="<number/date/time>" ]
```

| Parameter | Type    | Required   | Default   | Description                                                   |
| --------- | :-----: | :--------: | :-------: | ------------------------------------------------------------- |
| block     | String  | Yes        |           | Block name                                                    |
| threshold | Integer | No         | 0         | Send current value to openHAB, if changed more than threshold |
| force     | Boolean | No         | false     | Send current value to openHAB, independent if changed or not  |
| type      | String  | No         | "number"  | Configure how to interpret data fetched from LOGO! device     |

If parameter `type` is `"number"`, incomig data will be interpret as numeric value. In this case, the appropriate
channel must be linked to an `Number` item. Is `type` set to `"date"`, then the binding will try to interpret
incomig data as calendar date. If `type` is set to `"time"`, then incoming data will be tried to interpret as
time of day. For both `"date"` and `"time"` types, the appropriate channel must be linked to an `DateTime` item.

Follow block names are allowed for analog things:

| Type           | `0BA7`        | `0BA8`      | 
| -------------- | :-----------: | :---------: |
| Input          | `AI[1-8]`     | `AI[1-8]`   |
| Output         | `AQ[1-2]`     | `AQ[1-8]`   |
| Marker         | `AM[1-16]`    | `AM[1-64]`  |
| Network input  |               | `NAI[1-32]` |
| Network output |               | `NAQ[1-16]` |
| Memory (DWORD) | `VD[0-847]`   | `VD[0-847]` |
| Memory (WORD)  | `VW[0-849]`   | `VW[0-849]` |

## Channels
### Bridge
Each device have currently one channel `rtc`:

```
channel="plclogo:device:<DeviceId>:rtc"
```

This channel supports `DateTime` items only.

### Digital
Each digital thing have currently one channel `state`:

```
channel="plclogo:digital:<ThingId>:state"
```

Dependend on configured block type, channel supports one of two different item types: `Contact`
for inputs and `Switch` for outputs. Means, that for `I` and `NI` blocks `Contact` items must
be used. For other blocks simply use `Switch`, since they are bidirectional.

### Analog
Each analog thing have currently one channel `value`:

```
channel="plclogo:digital:<ThingId>:value"
```

This channel supports `Number` or `DateTime` items dependend on thing configuration.


## Examples
Configuration of one Siemens LOGO!

logo.things:

```
Bridge plclogo:device:Logo [ address="192.168.0.1", family="0BA8", localTSAP="0x3000", remoteTSAP="0x2000", refresh=100 ]
{
  Thing plclogo:digital:VB0_0 [ block="VB0.0" ]
  Thing plclogo:digital:VB0_1 [ block="VB0.1" ]
  Thing plclogo:digital:NI1   [ block="NI1" ]
  Thing plclogo:digital:NI2   [ block="NI2" ]
  Thing plclogo:digital:Q1    [ block="Q1" ]
  Thing plclogo:digital:Q2    [ block="Q2" ]
  Thing plclogo:analog:VW100  [ block="VW100", threshold=1, force=true ]
  Thing plclogo:analog:VW102  [ block="VW102", type="time" ]
}
```

logo.items:

```
// NI1 is mapped to VB0.0 address in LOGO!Soft Comfort 
// NI2 is mapped to VB0.1 address in LOGO!Soft Comfort 

Switch  LogoUp     {channel="plclogo:digital:VB0_0:state"}
Switch  LogoDown   {channel="plclogo:digital:VB0_1:state"}
Contact LogoIsUp   {channel="plclogo:digital:NI1:state"}
Contact LogoIsDown {channel="plclogo:digital:NI2:state"}
Switch  Output1    {channel="plclogo:digital:Q1:state"}
Switch  Output2    {channel="plclogo:digital:Q2:state"}
Number  Position   {channel="plclogo:analog:VW100:value"}
```

Configuration of two Siemens LOGO!

logo.things:

```
Bridge plclogo:device:Logo1 [ address="192.168.0.1", family="0BA8", localTSAP="0x3000", remoteTSAP="0x2000", refresh=100 ]
{
  Thing plclogo:digital:Logo1_VB0_0 [ block="VB0.0" ]
  Thing plclogo:digital:Logo1_VB0_1 [ block="VB0.1" ]
  Thing plclogo:digital:Logo1_NI1   [ block="NI1" ]
  Thing plclogo:digital:Logo1_NI2   [ block="NI2" ]
  Thing plclogo:digital:Logo1_Q1    [ block="Q1" ]
  Thing plclogo:digital:Logo1_Q2    [ block="Q2" ]
  Thing plclogo:analog:Logo1_VW100  [ block="VW100", threshold=1 ]
}
Bridge plclogo:device:Logo2 [ address="192.168.0.2", family="0BA8", localTSAP="0x3100", remoteTSAP="0x2000", refresh=100 ]
{
  Thing plclogo:digital:Logo2_VB0_0 [ block="VB0.0" ]
  Thing plclogo:digital:Logo2_VB0_1 [ block="VB0.1" ]
  Thing plclogo:digital:Logo2_NI1   [ block="NI1" ]
  Thing plclogo:digital:Logo2_NI2   [ block="NI2" ]
  Thing plclogo:digital:Logo2_Q1    [ block="Q1" ]
  Thing plclogo:digital:Logo2_Q2    [ block="Q2" ]
  Thing plclogo:analog:Logo2_VW100  [ block="VW100", threshold=1 ]
}
```

logo.items:

```
Switch  Logo1_Up       {channel="plclogo:digital:Logo1_VB0_0:state"}
Switch  Logo1_Down     {channel="plclogo:digital:Logo1_VB0_1:state"}
Contact Logo1_IsUp     {channel="plclogo:digital:Logo1_NI1:state"}
Contact Logo1_IsDown   {channel="plclogo:digital:Logo1_NI2:state"}
Switch  Logo1_Output1  {channel="plclogo:digital:Logo1_Q1:state"}
Switch  Logo1_Output2  {channel="plclogo:digital:Logo1_Q2:state"}
Number  Logo1_Position {channel="plclogo:analog:Logo1_VW100:value"}

Switch  Logo2_Up       {channel="plclogo:digital:Logo2_VB0_0:state"}
Switch  Logo2_Down     {channel="plclogo:digital:Logo2_VB0_1:state"}
Contact Logo2_IsUp     {channel="plclogo:digital:Logo2_NI1:state"}
Contact Logo2_IsDown   {channel="plclogo:digital:Logo2_NI2:state"}
Switch  Logo2_Output1  {channel="plclogo:digital:Logo2_Q1:state"}
Switch  Logo2_Output2  {channel="plclogo:digital:Logo2_Q2:state"}
Number  Logo2_Position {channel="plclogo:analog:Logo2_VW100:value"}
```