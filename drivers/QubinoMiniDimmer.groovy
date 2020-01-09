metadata {
	definition (name: "Qubino Mini Dimmer ZMNHHDx", namespace: "entire", author: "Entire") {
		capability "Actuator"
		capability "Switch"
		capability "Refresh"
		capability "Sensor"
    capability "Switch Level"
    capability "Power Meter"
		
		command "clear"
    attribute "updating","enum",["yes","no"]
		
    fingerprint deviceId: "0x55", inClusters: "0x5E,0x25,0x26,0x85,0x59,0x55,0x86,0x72,0x5A,0x70,0x32,0x71,0x73,0x98,0x9F,0x6C"
	}

  preferences {
    parameterMap().each {
      if(it.type != null && validForFirmware(it))
        input it.name, it.type, title:it.title, options:it.items, range:(it.min != null && it.max != null) ? "${it.min}..${it.max}" : null, defaultValue:it.default, required: false
    }
  }
}

private parameterMap() {
  [
    [name:"p1", index:1, mode:"zwave", size:1, type:"enum", default:0, title:"Input 1 switch type", items:[0:"Momentary switch", 1:"Toggle switch"]],
    [name:"p11", index:11, mode:"zwave", size:2, type:"number", default:0, min:0, max:32535, title:"Auto off in seconds 0 = disabled"],
    [name:"p12", index:12, mode:"zwave", size:2, type:"number", default:0, min:0, max:32535, title:"Auto on in seconds 0 = disabled"],
    [name:"p30", index:30, mode:"zwave", size:1, type:"enum", default:1, title:"State of the device after a power failure", items:[0:"Previous state", 1:"Off"]],
    [name:"p40", index:40, mode:"zwave", size:1, type:"number", default:10, min:0, max:100, title:"Power reporting threshold 0-100%"],
    [name:"p42", index:42, mode:"zwave", size:2, type:"number", default:0, min:0, max:32767, title:"Power reporting interval 0-65535s"],	
    [name:"p60", index:60, mode:"zwave", size:1, type:"number", default:0, min:0, max:98, title:"Minimum dimming value"],
    [name:"p61", index:61, mode:"zwave", size:1, type:"number", default:99, min:2, max:99, title:"Maximum dimming value"],
    [name:"p65", index:65, mode:"zwave", size:1, type:"number", default:1, min:1, max:127, title:"local dimming time (s)"],
    [name:"p66", index:66, mode:"zwave", size:2, type:"number", default:3, min:1, max:255, title:"hold and remote dimming time (s)"],
    [name:"p67", index:67, mode:"zwave", size:1, type:"enum", default:0, title:"Ignore start level", items:[0:"No", 1:"Yes"]],
    [name:"p68", index:68, mode:"zwave", size:1, type:"number", default:0, min:0, max:127, title:"dimming duration (s)"],
    
    [name:"enableDebugging", mode:"settings", type:"bool", title:"Enable Debug Logging", default:"true"],
    [index:1, mode:"association"],
  ]
}

//////////////
// clear event
def clear() {
  logDebug("clear")
  state.clear()
  state.currentProperties = [:]
  device.removeSetting("firmwareVersion")  
}

////////////////
// Updated event
def updated() {
  logDebug("updated")
  def cmds = updateSettings()
	if (cmds != []) 
		commands(cmds, 750)
}

//////////////////
// Installed event
def installed() {
	logDebug("Installed")
	state.currentProperties = [:]
}

////////////////////
// Turn the light on
def on() {
  log.info("Switching on")
  commands([
    zwave.basicV1.basicSet(value: 0xff)
  ])
}


/////////////////////
// Turn the light off
def off() {
  log.info("Switching off")
  commands([
    zwave.basicV1.basicSet(value: 0)
  ])
}


///////////////////////////
// Refresh the switch level
def refresh() {
  logDebug("refresh")
  commands([
    zwave.switchMultilevelV1.switchMultilevelGet()
  ])
}


/////////////////////////////
// Set the level of the light
def setLevel(level) {
  commands([
    zwave.basicV1.basicSet(value: Math.min(99, level.toInteger()))
  ])
}

/////////////////////////////
// Set the level of the light
def setLevel(level, duration) {
  commands([
    zwave.switchMultilevelV2.switchMultilevelSet(value: Math.min(99, level.toInteger()), dimmingDuration: duration),
    zwave.switchMultilevelV1.switchMultilevelGet()
  ])
}

//////////////////////////
// Handler for BasicReport
def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
  logDebug("basicv1.BasicReport '${cmd}'")
}

/////////////////////////////////////
// Handler for SwitchMultilevelReport
def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd, ep = null) {
	def result = [], value
	
  logDebug("switchmultilevelv3.SwitchMultilevelReport $ep '${cmd}'")
	value = (cmd.value ? "on" : "off")
  
	if(device.currentValue("switch") != value)
	  log.info "Dimmer is ${value}"

  result << createEvent(name: "switch", value: value, descriptionText: "$device.displayName was turned $value")
  
	if(cmd.value != null)
		result << createEvent(name: "level", value: cmd.value, unit: "%")
	return result
}

/////////////////////////////////
// Handler for SwitchBinaryReport
def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
  logDebug("switchbinaryv1.SwitchBinaryReport '${cmd}'")
}


//////////////////////////////////
// Handler for SwitchMultilevelSet
def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelSet cmd) {
  logDebug("switchmultilevelv3.SwitchMultilevelSet '${cmd}'")
}

/////////////////////////////////
// Handler for SensorBinaryReport
def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
	logDebug "sensorbinaryv2.SensorBinaryReport $cmd"
}

//////////////////////
// meterv3.MeterReport
def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd, ep=null) {
  def result = []

  logDebug "meterv3.MeterReport $ep $cmd"
	
	if (cmd.scale == 0){
		log.info("Energy usaged ${cmd.scaledMeterValue}kWh")
		result << createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
	}
	else if (cmd.scale == 1) {
		log.info("Energy usaged ${cmd.scaledMeterValue}kVAh")
		result << createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kVAh")
	}
	else if (cmd.scale == 2) {
		if(device.currentValue("power") != cmd.scaledMeterValue)
			log.info("Power usaged ${cmd.scaledMeterValue}W")
		result << createEvent(name: "power", value: cmd.scaledMeterValue, unit: "W")
	}
	else
	 logDebug("Unknown MeterReport scale: ${cmd.scale}")
  return result
}


////////////////////////////////////
// Standard parse to command classes
def parse(String description) {
  def cmd = zwave.parse(description)
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
private commands(cmds, delay=500) {
	delayBetween(cmds.collect{formatCmd(it)}, delay)
}

///////////////////////////////////////////////
// Format the command according to the security
private formatCmd(hubitat.zwave.Command cmd) {
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
            cmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: it.default, parameterNumber: it.index, size: it.size)
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
  logDebug("versionv1.VersionReport '${cmd}'")
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

///////////////////
// Integer to Array
def integer2Array(value, size) {
	
	if(value instanceof String)
    value = value.toInteger()

	switch(size) {
	case 1:
		[value & 0xFF]
    break
	case 2:
		[(value >> 8) & 0xFF, value & 0xFF]
		break
	case 4:
		[(value >> 24) & 0xFF, (value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF]
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

