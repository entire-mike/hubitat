/*
  Fibaro Motion Sensor FGMS-001

  Based on code from the multiple people from the Hubitat Community and reference drivers from Hubitat
  These include but not limited to 
    Jean-Jacques GUILLEMAUD, Artur Draga, Bryan Turcotte, Robin Winbourne, Eric Maycock

  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
  in compliance with the License. You may obtain a copy of the License at:
 
  http://www.apache.org/licenses/LICENSE-2.0
 
  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
  for the specific language governing permissions and limitations under the License.

*/

metadata {
  definition (name: "Fibaro Motion Sensor FGMS-001", namespace: "entire", author: "Entire") {
    capability "Battery"
    capability "Illuminance Measurement"
    capability "Motion Sensor"
    capability "Sensor"
    capability "Tamper Alert"
    capability "Temperature Measurement"

    attribute "health", "enum", ["alive", "dead"]
    attribute "updating", "enum", ["yes", "no"]

    command "clear"
		
    fingerprint mfr:"010F", prod:"0800", deviceId: "1001", inClusters: "0x30,0x84,0x85,0x80,0x8F,0x56,0x72,0x86,0x70,0x8E,0x31,0x9C"
    fingerprint mfr:"010F", prod:"0801", deviceId:"1001", inClusters:"0x5E,0x20,0x86,0x72,0x5A,0x59,0x85,0x73,0x84,0x80,0x71,0x56,0x70,0x31,0x8E,0x22,0x30,0x9C,0x98,0x7A"    
  }
	
  preferences {
    parameterMap().each {
      if(it.type != null && validForFirmware(it))
        input it.name, it.type, title:it.title, options:it.items, range:(it.min != null && it.max != null) ? "${it.min}..${it.max}" : null, defaultValue:it.default, required: false
    }
  }
}

/////////////////////////////////////////////////////
// Parameters 
private parameterMap() {
  [
    [name:"wakeUpInterval", mode:"wakeup", type:"number", default:7200, min:1, max:65535, title:"Wake Up interval in seconds (default 7200)"], 
    [name:"p1", index:1, mode:"zwave", size:1, type:"number", default:15, min:8, max:255, title:"Sensitivity. Default 10 (8-255)"], 
    [name:"p2", index:2, mode:"zwave", size:1, type:"number", default:15, min:0, max:15, title:"Motion blind time. Default 15 (0 - 15)"], 
    [name:"p3", index:3, mode:"zwave", size:1, type:"enum", default:1, min:0, max:3, title:"Movement pulse count.", items:[0:"1", 1:"2 (default)", 2:"3", 3:"4", 4:"5"]],
    [name:"p4", index:4, mode:"zwave", size:1, type:"enum", default:2, min:0, max:3, title:"Window time", items:[0: "4s", 1: "8s", 2: "12s (default)", 3: "16s"]], 
    [name:"p6", index:6, mode:"zwave", size:2, type:"number", default:30, min:1, max:65535, title:"Motion cancel time"], 
    [name:"p8", index:8, mode:"zwave", size:1, type:"enum", default:0, min:0, max:2, title:"PIR Operating Mode", items:[0: "Always active (default)", 1: "Active during day", 2: "Active during night"]], 
    [name:"p9", index:9, mode:"zwave", size:2, type:"number", default:200, min:1, max:65535, title:"Night / day light intensity"], 
    [name:"p40", index:40, mode:"zwave", size:2, type:"number", default:200, min:0, max:65535, title:"Intensity report threshold (lux)"], 
    [name:"p42", index:42, mode:"zwave", size:2, type:"number", default:0, min:0, max:65535, title:"Intensity report interval (seconds)"], 
    [name:"p60", index:60, mode:"zwave", size:1, type:"number", default:10, min:0, max:255, title:"Temperature report threshold (0.1C)"], 
    [name:"p62", index:62, mode:"zwave", size:2, type:"number", default:900, min:0, max:65535, title:"Temperature measurement interval (seconds)"], 
    [name:"p64", index:64, mode:"zwave", size:2, type:"number", default:0, min:0, max:65535, title:"Temperature report interval (seconds)"], 
    [name:"p66", index:66, mode:"zwave", size:2, type:"number", default:0, min:-100, max:100, title:"Temperature offset (0.1C)"], 
    [name:"p89", index:89, mode:"zwave", size:1, type:"enum", default:1, min:0, max:1, title:"Visual Tamper alarm ", items:[0:"No", 1:"Yes"]],
    [name:"enableDebugging", mode:"settings", type:"bool", title:"Enable Debug Logging", default:"true"],
    [index:1, mode:"association"],
    [index:2, fw:"2.4-2.8", mode:"association"],
    [index:3, fw:"2.4-2.8", mode:"association"],
		[name:"battery", mode:"battery"]
  ]
}


/////////////////////////////////////////////////////
// Clear event
def clear() {
  logDebug("clear")
  state.clear()
  state.currentProperties = [:]
  device.removeSetting("firmwareVersion")
}

/////////////////////////////////////////////////////
// Updated event
def updated() {
  logDebug("updated")
  unschedule()
  schedule("0 0 * ? * * *", healthCheck)
}

/////////////////////////////////////////////////////
// Installed event
def installed() {
  logDebug("Installed")
}

/////////////////////////////////////////////////////
// basicv1.BasicSet
def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
  def motion
	
  motion = (cmd.value) ? "active" : "inactive"
  if(device.currentValue("motion") != motion)
    log.info("motion '${motion}'")
  sendEvent(name: "motion", value: motion)
}

/////////////////////////////////////////////////////
// sensorbinaryv2.SensorBinaryReport
def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
  logDebug "sensorbinaryv2.SensorBinaryReport $cmd"
}

/////////////////////////////////////////////////////
// sensormultilevelv5.SensorMultilevelReport
def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
  logDebug("sensormultilevelv5.SensorMultilevelReport ${cmd}")
	
  switch (cmd.sensorType as Integer) {
  case 1:
    def cmdScale = cmd.scale == 1 ? "F" : "C"
    sendEvent(name: "temperature", unit: "°${getTemperatureScale()}", value: convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision), displayed: true)
    log.info "Temperature ${cmd.scaledSensorValue}${cmdScale}"
    break
  case 3:
    sendEvent(name: "illuminance", value: cmd.scaledSensorValue.toInteger().toString(), unit:"lux", displayed: true)
    log.info "Illuminance ${cmd.scaledSensorValue}lux"
    break
  }
}

/////////////////////////////////////////////////////
// sensormultilevelv1.SensorMultilevelReport
def zwaveEvent(hubitat.zwave.commands.sensormultilevelv1.SensorMultilevelReport cmd) {
  logDebug("sensormultilevelv1.SensorMultilevelReport ${cmd}")
	
  switch (cmd.sensorType as Integer) {
  case 1:
    def cmdScale = cmd.scale == 1 ? "F" : "C"
    sendEvent(name: "temperature", unit: "°${getTemperatureScale()}", value: convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision))
    log.info "Temperature ${cmd.scaledSensorValue}${cmdScale}"
    break
  case 3:
    if(device.currentValue("illuminance") != cmd.scaledSensorValue.toInteger())
      log.info "Illuminance ${cmd.scaledSensorValue}lux"
    sendEvent(name: "illuminance", value: cmd.scaledSensorValue.toInteger(), unit:"lux")
    break
  }
}


/////////////////////////////////////////////////////
//notificationv3.NotificationReport
def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
	logDebug "NotificationReport received ${cmd}"
  
  switch (cmd.notificationType) {
  case 7:
    motion = (cmd.event) ? "active" : "inactive"
    if(device.currentValue("motion") != motion)
      log.info("motion '${motion}'")
    sendEvent(name: "motion", value: motion)
    break
	}
}

def zwaveEvent(hubitat.zwave.commands.sensoralarmv1.SensorAlarmReport cmd) {
  logDebug("sensoralarmv1.SensorAlarmReport '${cmd}'")
}

////////////////////////////////////
// Standard parse to command classes
def parse(String description) {
  setHealth("alive")
  def cmd = zwave.parse(description, [0x86:1])
  if (cmd) {
    logDebug("Parsed '${description}'")
    return zwaveEvent(cmd)
  }
  logDebug("Failed to parse '${description}'")
}

/////////////////////
// Standard catch all
def zwaveEvent(hubitat.zwave.Command cmd) {
  log.warn("Unhandled event $cmd")
}

//////////////////////////////////////////
// securityv1.SecurityMessageEncapsulation
def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  logDebug("securityv1.SecurityMessageEncapsulation '${cmd}'")
  def encapsulatedCommand = cmd.encapsulatedCommand( )
  if (encapsulatedCommand)
    return zwaveEvent(encapsulatedCommand)
  else
    log.warn "Failed to extract secure message from ${cmd}"
}

//////////////////////////
// crc16encapv1.Crc16Encap
def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
  def encapsulatedCommand = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data)
  if (encapsulatedCommand)
    zwaveEvent(encapsulatedCommand)
  else
    log.warn "Unable to extract CRC16 command from ${cmd}"
}

//////////////////////////////////
// create a delay between commands
private commands(commands, delay=500) {
  delayBetween(commands.collect{command(it)}, delay)
}

///////////////////////////////////////////////
// Format the command according to the security
private command(hubitat.zwave.Command cmd) {
	if (getDataValue("zwaveSecurePairingComplete") == "true")
    zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
  else
    cmd.format()
}

///////////////////////////////////
// format a message for an endpoint
private endpoint(hubitat.zwave.Command cmd, endpoint) {
  zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:endpoint).encapsulate(cmd)
}

//////////////////////////////////////
// multichannelv3.MultiChannelCmdEncap
def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
  def encapsulatedCommand = cmd.encapsulatedCommand( )
  if (encapsulatedCommand) {
    logDebug ("Command from endpoint ${cmd.sourceEndPoint}: ${encapsulatedCommand}")
    zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
  }
  else
    log.warn "Could not extract multi channel command from ${cmd}"
}

////////////////////////////////////////
// Update any settings that have changed
def updateSettings()
{
	def cmds = []
	def updating = "no"

	if(settings.firmwareVersion == null)
    cmds << zwave.versionV1.versionGet()
    
	// Set the individual settings
	parameterMap().each {
		if (validForFirmware(it)) {
      if (it.mode == "zwave") {
        if (state.currentProperties."${it.name}" == null) {
          updating = "yes"
          if(settings."${it.name}" != null) {
            log.info("Setting parameter ${it.index} to " + settings."${it.name}")
            cmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: settings."${it.name}".toInteger(), parameterNumber: it.index, size: it.size)
          }
          else if(it.default != null) {
            log.info("Setting parameter ${it.index} to ${it.default}")
            cmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: it.default.toInteger(), parameterNumber: it.index, size: it.size)
          }
          cmds << zwave.configurationV1.configurationGet(parameterNumber: it.index)
        }
        else if (settings."${it.name}" != null && state.currentProperties."${it.name}" != settings."${it.name}".toInteger()) { 
          updating = "yes"
          log.info("Setting parameter ${it.index} to " + settings."${it.name}" + " last value " + state.currentProperties."${it.name}")
          cmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: settings."${it.name}".toInteger(), parameterNumber: it.index, size: it.size)
          cmds << zwave.configurationV1.configurationGet(parameterNumber: it.index)
        } 
      }
      else if(it.mode == "association" && state.currentProperties."a${it.index}" != zwaveHubNodeId) {
			  updating = "yes"
        log.info("Updating association group ${it.index}")
        cmds << zwave.associationV2.associationSet(groupingIdentifier: it.index, nodeId: [zwaveHubNodeId])
        cmds << zwave.associationV2.associationGet(groupingIdentifier: it.index)
		  }
      else if(it.mode == "removeAssociation") {
        log.info("Removing association group ${it.index}")
        cmds << zwave.associationV2.associationRemove(groupingIdentifier: it.index, nodeId: [])
        cmds << zwave.associationV2.associationGet(groupingIdentifier: it.index)
		  }
      else if(it.mode == "wakeup" && validForFirmware(it) && state.currentProperties.wI != settings.wakeUpInterval) {
        cmds << zwave.wakeUpV2.wakeUpIntervalSet(seconds:settings.wakeUpInterval, nodeid:zwaveHubNodeId)
        cmds << zwave.wakeUpV2.wakeUpIntervalGet( )
        updating = "yes"
      }
      else if(it.mode == "battery") {
        cmds << zwave.batteryV1.batteryGet()
      }
    }
  }
  sendEvent(name:"updating", value: updating)
	return cmds
}

//////////////////////////////////
// Standard Version1 VersionReport
def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {	
  log.info("versionv1.VersionReport '${cmd}'")
  state.firmwareVersion = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
  state.zWaveProtocolVersion = "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
  log.info "Firmware Version ${state.firmwareVersion}"
  device.updateSetting("firmwareVersion", "${cmd.applicationVersion}.${cmd.applicationSubVersion}")
}

/////////////////////////
// Standard debug logging
private def logDebug(message) {
  if(settings.enableDebugging == true)
    log.debug "$message"
}

//////////////////////////////
// Scale temperature for units
def temperature(value, units) {
  if (location.temperatureScale != units) {
    if (location.temperatureScale == "F")
      value = value * 1.8 + 32
    else
      value = (value - 32) / 1.8
  }
  return value
}


////////////////////////////
// Standard config v2 report
def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
  logDebug("configurationv2.ConfigurationReport $cmd")
  updateProperty(cmd)
}

///////////////////////////////////
// store a parameter update command
def updateProperty(cmd)
{
  value = array2Integer(cmd.configurationValue)
  param = parameterMap().find{it.index == cmd.parameterNumber}
  if(param && param.manual != true) {
    state.currentProperties."p${cmd.parameterNumber}" = value
    if(param.overwrite == true) {
      if(param.type == "enum") {
        device.updateSetting("${param.name}", "${value}")
        log.info "overwritten parameter ${cmd.parameterNumber} with '${value}'"
      }
      else {
  	    device.updateSetting("${param.name}", value)
        log.info "overwritten parameter ${cmd.parameterNumber} with ${value}"
      }
    }
    else {
      settings = SettingsPending(state.currentProperties)
      if(settings == 0) {
        log.info("parameter ${cmd.parameterNumber} reported value ${array2Integer(cmd.configurationValue)}. All parameters updated")
        sendEvent(name:"updating", value:"no")
      }
      else
        log.info("parameter ${cmd.parameterNumber} reported value ${array2Integer(cmd.configurationValue)}. ${settings} setting(s) left")
    }
  }
  else
    log.info "parameter ${cmd.parameterNumber} reported a value of ${value}"
}

//////////////////////////////////
// associationv2.AssociationReport
def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
  logDebug("Association for Group ${cmd.groupingIdentifier} = ${cmd.nodeId[0]}")
	
  state.currentProperties."a${cmd.groupingIdentifier}" = cmd.nodeId[0]
  settings = SettingsPending(state.currentProperties)
  if(settings == 0) {
    log.info "Association Group ${cmd.groupingIdentifier} reported node ${cmd.nodeId[0]}. All parameters updated"
    sendEvent(name:"updating", value:"no")
  }
  else
    log.info "Association Group ${cmd.groupingIdentifier} reported node ${cmd.nodeId[0]}. ${settings} setting(s) left"
}

///////////////////////////////////////
// Standard wakeupv1 WakeUpNotification
def zwaveEvent(hubitat.zwave.commands.wakeupv1.WakeUpNotification cmd) {
  log.info "Device woke up"
	
  def cmds = updateSettings()
  cmds << zwave.wakeUpV1.wakeUpNoMoreInformation()
  return response(commands(cmds))
}

///////////////////////////////////////
// Standard wakeupv2 WakeUpNotification
def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
  log.info "Device woke up"
	
  def cmds = updateSettings()
  cmds << zwave.wakeUpV2.wakeUpNoMoreInformation()
  return response(commands(cmds))
}

//////////////////////////
// Standard battery report
def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
  log.info "Battery ${cmd.batteryLevel}%"
  createEvent(name:"battery", value:cmd.batteryLevel, unit:"%")
}

////////////////////////////////
// wakeUpV2.WakeUpIntervalReport
def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {
  logDebug "Wake up interval = ${cmd}"
  
  state.currentProperties.wI = cmd.seconds
  settings = SettingsPending(state.currentProperties)
  if(settings == 0) {
    log.info "Wake up interval reported ${cmd.seconds}s. All parameters updated"
    sendEvent(name:"updating", value:"no")
  }
  else
    log.info "Wake up interval reported ${cmd.seconds}s. ${settings} setting(s) left"
}

//////////////////////////
// Check if all up to date
def SettingsPending(currentProperties)
{
  def Pending = 0
	
	parameterMap().each {
    if(validForFirmware(it)) {
      if (it.mode == "zwave"){
			  if (currentProperties."${it.name}" == null)
			    Pending = Pending + 1
			  else if (it.type == null && currentProperties."${it.name}" != it.default)
			    Pending = Pending + 1
			  else if (it.type != null && settings."${it.name}" != null && currentProperties."${it.name}" != settings."${it.name}".toInteger())
          Pending = Pending + 1
		  }
      else if(it.mode == "association" && currentProperties."a${it.index}" != zwaveHubNodeId)
        Pending = Pending + 1
      else if(it.mode == "wakeup" && state.currentProperties.wI != settings.wakeUpInterval) 
        Pending = Pending + 1
    }
	}
	return Pending
}


///////////////////
// setHealth
def setHealth(health) {
  if(device.currentValue("health") != health)
    sendEvent(name:"health", value: health)
}

///////////////////
// healthCheck
def healthCheck() {
  // If battery device then use the wake up interval
  if(settings.wakeUpInterval != null) {
    if((now() - device.getLastActivity().getTime()) / 1000 > (settings.wakeUpInterval * 1.05))
      setHealth("dead")
  }
//  else {
//    log.info "healthCheck():Checking ${now() - device.getLastActivity()} > ${settings.wakeUpInterval * 1.05}"
//    if((now() - device.getLastActivity()) > (settings.wakeUpInterval * 1.05))
//      setHealth("dead")
//  }
}

///////////////////
// Array to integer
def array2Integer(array) { 
  switch(array.size()) {
  case 1:
    (byte)array[0]
    break
  case 2:
    (short)(((array[0] & 0xFF) << 8) | (array[1] & 0xFF))
    break
  case 4:
    (int)(((array[0] & 0xFF) << 24) | ((array[1] & 0xFF) << 16) | ((array[2] & 0xFF) << 8) | (array[3] & 0xFF))
    break
  }
}


///////////////////////////////////////////////
// Check if parameter is valid for the firmware
def validForFirmware(item) {
  def ret = false
  if (item.fw == null)
    ret = true
  else if (settings != null && settings.firmwareVersion != null) {
    ver = settings.firmwareVersion.tokenize(".")*.toInteger()
    list = item.fw.split(",")
    list.each {
      range = it.split("-")
      if(range.length == 1) {
        checkVer = range[0].tokenize(".")*.toInteger()
        if (ver[0] == checkVer[0] && ver[1] == checkVer[1])
          ret = true
      }
      else if (range.length == 2) {
        checkVerMin = range[0].tokenize(".")*.toInteger()
        checkVerMax = range[1].tokenize(".")*.toInteger()
        if ((ver[0] > checkVerMin[0] || (ver[0] == checkVerMin[0] && ver[1] >= checkVerMin[1])) && (ver[0] < checkVerMax[0] || (ver[0] == checkVerMax[0] && ver[1] <= checkVerMax[1])))
          ret = true
      }
    }
  }
  return(ret)
}
