/**
 *  Fibaro ZW5 Motion Sensor - Enhanced Acceleration Reporting
 *
 *  Device Type:	Fibaro ZW5 Motion Sensor - Enhanced
 *  Author: 		Tim Yuhl
 *
 *  Base upon work by:  Todd Wackford, Ronald Gouldner for a SmartThings driver
 *  					Logging code: Thanks to ericvitale
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 *  files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 *  modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 *  WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *  History:
 *
 *  v1.0.0 - 7-Oct-2020 - Port from SmartThings, enhancements to report acceleration active, inactive, clean-up to
 *  get it working in Hubitat
 *
 */

import groovy.transform.Field

String driverVersion()   { return "1.0.0" }
def setVersion(){
	state.name = "Fibaro ZW5 Motion Sensor - Enhanced"
	state.version = "1.0.0"
}

@Field static Map commandClassVersions = [
		0x72: 2,    // ManufacturerSpecific
		0x31: 2,    // Sensor Multilevel
		0x30: 1,    // Sensor Binary
		0x84: 1,	// WakeUp
		0x9C: 1,    // Alarm Sensor
		0x70: 2,	// Configuration
		0x80: 1,	// Battery
		0x86: 1,	// Version (2)
		0x7A: 1,	// FirmwareUpdateMd (3)
		0x56: 1     // CRC-16 Encapsulation (deprecated)
]

preferences {

	input title: "Instructions", description: "Quickly click the sensor button (inside) 3 times to wake the device (blue light displays) and then select the \"Configure\" button after clicking done on this page."

	input("sensitivity", "number", title: "Motion Sensitivity", defaultValue:10)
	input("blindTime", "number", title: "Motion Retrigger Time in seconds", defaultValue:15)
	input("vibrationSensitivity", "number", title: "Vibration Sensitivity - Lower values are more sensitive", defaultValue:15)
	input("vibrationBlindTime", "number", title: "Vibration Blind Time in seconds", defaultValue:5)
	input("vibrationReportInterval", "number", title: "Vibration Reporting Interval in seconds", defaultValue:30)
	input("illumReportThresh", "number", title: "Light Report Threshold in lux", defaultValue:200)
	input("illumReportInt", "number", title: "Light Reporting Interval in seconds", defaultValue:0)
	input("ledOnOff", "enum", title: "LED On/Off", default:"On", options: ["On","Off"])
	input("ledModeFrequency", "enum", title: "Motion LED mode", default: "Once", options: ["Once","Long-Short","Long-Two Short"])
	input("ledModeColor", "enum", title: "Motion LED Color", default:"Temp", options: ["Temp","Flashlight","White","Red","Green","Blue","Yellow","Cyan","Magenta"])
	input("ledBrightness", "number", title: "LED Brightness level", defaultValue:50)
	input("tamperLedOnOff", "enum", title: "Vibration LED", default:"On", options: ["On","Off"])

	input "logging", "enum", title: "Log Level", required: false, defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
}

/**
 * Sets up metadata
 *
 * @param none
 *
 * @return none
 */
metadata {
	definition (name: "Fibaro ZW5 Motion Sensor - Enhanced", namespace: "tyuhl", author: "Tim Yuhl") {
		capability 	"Motion Sensor"
		capability 	"Temperature Measurement"
		capability 	"Acceleration Sensor"
		capability 	"Configuration"
		capability 	"Illuminance Measurement"
		capability 	"Sensor"
		capability 	"Battery"
		capability	"Refresh"

		attribute   "vibration", "enum", ["active", "inactive"]
		command		"configure"

		fingerprint deviceId: "0x2001", inClusters: "0x30,0x84,0x85,0x80,0x8F,0x56,0x72,0x86,0x70,0x8E,0x31,0x9C,0xEF,0x30,0x31,0x9C"
	}
}

/**
 * Configures the device to settings needed at device discovery time.
 *
 * @param none
 *
 * @return none
 */
def configure() {
	log("Configuring Device", "trace")
	def cmds = []

	// send associate to group 3 to get sensor data reported only to hub
	cmds << zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:[zwaveHubNodeId]).format()

	//  Set Motion Sensitivity (1) Values 8-255 default 10
	log("Setting sensitivity $sensitivity", "debug")
	def senseValue = sensitivity as int
	if (senseValue < 8 || senseValue > 255) {
		log("Unknown sensitivity-Setting to default of 10", "warn")
		senseValue = 10
	}
	log("Setting Param 1 to $senseValue", "debug")
	cmds << zwave.configurationV1.configurationSet(configurationValue: [senseValue], parameterNumber: 1, size: 1).format()

	//  Set Blind Time (3) Value 0-15 default 15
	log("Setting blind time $blindTime", "debug")
	def blindValue = blindTime as int
	if (blindValue < 0 || blindValue > 15) {
		log("Blind time outside allowed values-Setting to default of 15", "warn")
		blindValue = 15
	}
	log("Setting Param 2 to $blindValue", "debug")
	cmds << zwave.configurationV1.configurationSet(configurationValue: [blindValue], parameterNumber: 2, size: 1).format()

	// Set Vibration Sensitivity
	log("Setting Vibration Sensitivity to $vibrationSensitivity", "debug")
	def vibSensValue = vibrationSensitivity as int
	if (vibSensValue < 0 || vibSensValue > 121) {
		log("Vibration Sensitivity out of range, setting to default", "warn")
		vibSensValue = 15
	}
	cmds << zwave.configurationV1.configurationSet(configurationValue: [vibSensValue], parameterNumber: 20, size: 1).format()

	// Set Vibration Blind Time
	log("Setting Vibration Blind Time to $vibrationBlindTime", "debug")
	def vibBlindTime = vibrationBlindTime as int
	if (vibBlindTime < 1 || vibBlindTime > 65535) {
		log("Vibration Blind Time out of range, setting to default", "warn")
		vibBlindTime = 30
	}
	cmds << zwave.configurationV1.configurationSet(configurationValue: [vibCancelTime], parameterNumber: 22, size: 2).format()

	// PIR Operating Mode hard coded to default
	log("Setting pir operating mode $pirOperatingMode", "debug")
	cmds << zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 8, size: 1).format()

	// Night/Day Operation Mode hard coded to default
	log("Setting Night/Day threshold to default", "debug")
	cmds << zwave.configurationV1.configurationSet(configurationValue: [200], parameterNumber: 9, size: 2).format()

	// turn on tamper sensor with active/inactive reports (use it as an acceleration sensor) default is 0, or off
	log("Setting Param 24 to value of 1 - HARD CODED Cancellation is reported", "debug")
	cmds << zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 24, size: 1).format()

	log("Illum Report Threshole Preference illumReportThresh=$illumReportThresh", "debug")
	def illumReportThreshAsInt = illumReportThresh.toInteger()
	if (illumReportThreshAsInt >= 0 && illumReportThreshAsInt <= 65535 ) {
		short illumReportThreshLow = illumReportThreshAsInt & 0xFF
		short illumReportThreshHigh = (illumReportThreshAsInt >> 8) & 0xFF
		def illumReportThreshBytes = [illumReportThreshHigh, illumReportThreshLow]
		log("Setting Param 40 to $illumReportThreshBytes", "debug")
		cmds << zwave.configurationV1.configurationSet(configurationValue: illumReportThreshBytes, parameterNumber: 40, size: 2).format()
	}
	else {
		log("Illumination Report Threshold out of range - ignored", "warn")
	}

	log("Illum Interval Preference illumReportInt=$illumReportInt", "debug")
	def illumReportIntAsInt = illumReportInt.toInteger()
	if (illumReportIntAsInt >= 0 && illumReportIntAsInt <= 65535 ) {
		short illumReportIntLow = illumReportIntAsInt & 0xFF
		short illumReportIntHigh = (illumReportIntAsInt >> 8) & 0xFF
		def illumReportBytes = [illumReportIntHigh, illumReportIntLow]
		log("Setting Param 42 to $illumReportBytes", "debug")
		cmds << zwave.configurationV1.configurationSet(configurationValue: illumReportBytes, parameterNumber: 42, size: 2).format()
	}
	else {
		log("Illumination Reporting Interval out of range - ignored", "warn")
	}

	// temperature change report threshold (0-255 = 0.1 to 25.5C) default is 1.0 Celcius, setting to .5 Celcius
	log("Setting Param 60 to value of 5 - HARD CODED", "debug")
	cmds << zwave.configurationV1.configurationSet(configurationValue: [5], parameterNumber: 60, size: 1).format()

	// Set Parameter 80, covers "LED On/Off, LED Frequency, and LED Mode
	if (ledOnOff == "Off") {
		log("Setting LED off", "debug")
		// 0 = LED Off signal mode
		log("Setting Param 80 to 0", "debug")
		cmds << zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 80, size: 1).format()
	} else {
		log("Setting LED on", "debug")
		// ToDo Add preference for other available Led Signal Modes
		def ledModeConfigValue=0
		log("ledModeFrequency = $ledModeFrequency", "debug")
		log("ledModeColor = $ledModeColor", "debug")
		if (ledModeFrequency == "Once") {
			if (ledModeColor == "Temp") {
				ledModeConfigValue=1
			} else if (ledModeColor == "Flashlight") {
				ledModeConfigValue=2
			} else if (ledModeColor == "White") {
				ledModeConfigValue=3
			}else if (ledModeColor == "Red") {
				ledModeConfigValue=4
			}else if (ledModeColor == "Green") {
				ledModeConfigValue=5
			}else if (ledModeColor == "Blue") {
				ledModeConfigValue=6
			}else if (ledModeColor == "Yellow") {
				ledModeConfigValue=7
			}else if (ledModeColor == "Cyan") {
				ledModeConfigValue=8
			}else if (ledModeColor == "Magenta") {
				ledModeConfigValue=9
			} else {
				log("Unknown LED Color-Setting LED Mode to default of 10", "warn")
				ledModeConfigValue=10
			}
		} else if (ledModeFrequency == "Long-Short") {
			if (ledModeColor == "Temp") {
				ledModeConfigValue=10
			} else if (ledModeColor == "Flashlight") {
				ledModeConfigValue=11
			} else if (ledModeColor == "White") {
				ledModeConfigValue=12
			} else if (ledModeColor == "Red") {
				ledModeConfigValue=13
			} else if (ledModeColor == "Green") {
				ledModeConfigValue=14
			} else if (ledModeColor == "Blue") {
				ledModeConfigValue=15
			} else if (ledModeColor == "Yellow") {
				ledModeConfigValue=16
			} else if (ledModeColor == "Cyan") {
				ledModeConfigValue=17
			} else if (ledModeColor == "Magenta") {
				ledModeConfigValue=18
			} else {
				log("Unknown LED Color-Setting LED Mode to default of 10", "warn")
				ledModeConfigValue=10
			}
		} else if (ledModeFrequency =="Long-Two Short") {
			if (ledModeColor == "Temp") {
				ledModeConfigValue=19
			} else if (ledModeColor == "Flashlight") {
				log("Flashlight Mode selected with Frequency Long-Two Short setting ledMode to 11-flashlight mode", "info")
				ledModeConfigValue=11
			} else if (ledModeColor == "White") {
				ledModeConfigValue=20
			} else if (ledModeColor == "Red") {
				ledModeConfigValue=21
			} else if (ledModeColor == "Green") {
				ledModeConfigValue=22
			} else if (ledModeColor == "Blue") {
				ledModeConfigValue=23
			} else if (ledModeColor == "Yellow") {
				ledModeConfigValue=24
			} else if (ledModeColor == "Cyan") {
				ledModeConfigValue=25
			} else if (ledModeColor == "Magenta") {
				ledModeConfigValue=26
			} else {
				log("Unknown LED Color-Setting LED Mode to default of 10", "warn")
				ledModeConfigValue=10
			}
		} else {
			log("Unknown LED Frequencey-Setting LED Mode to default of 10", "warn")
			ledModeConfigValue=10
		}
		log("Setting Param 80 to $ledModeConfigValue", "debug")
		cmds << zwave.configurationV1.configurationSet(configurationValue: [ledModeConfigValue], parameterNumber: 80, size: 1).format()
	}

	//  Set Brightness Parameter (81) Percentage 0-100
	log("LED Brightness $ledBrightness", "debug")
	def brightness = ledBrightness as int
	if (brightness<0) {
		log("LED Brightness less than 0, setting to 1", "warn")
		brightness=1
	}
	if (brightness>100) {
		log("LED Brightness greater than 100, setting to 100", "warn")
		brightness=100
	}

	log("Setting Param 81 to $brightness", "debug")
	cmds << zwave.configurationV1.configurationSet(configurationValue: [brightness], parameterNumber: 81, size: 1).format()

	if (tamperLedOnOff == "Off") {
		log("Setting Tamper LED off", "debug")
		log("Setting Param 89 to 0", "debug")
		cmds << zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 89, size: 1).format()
	} else {
		log("Setting Tamper LED on", "debug")
		log("Setting Param 89 to 1", "debug")
		cmds << zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 89, size: 1).format()
	}
/*
	configParams.each {
		cmds += updateConfigVal(it)
	}
*/
	state.pendingRefresh = false

	return cmds ? delayBetween(cmds, 500) : []
}
/*
private updateConfigVal(param) {
	def result = []
	def configVal = state["configVal${param.num}"]

	if (state.pendingRefresh || ("${configVal}" != "${param.value}")) {
		log("Changing ${param.name} (#${param.num}) from ${configVal} to ${param.value}", "debug")
		result << configSetCmd(param)
		result << configGetCmd(param)
	}
	return result
}
*/

def refresh() {
	log("Refresh called", "trace")
//	dbCleanUp()

	if (state.pendingRefresh) {
		configParams.each {
			state."configVal${it.num}" = null
		}
	}
	log("The sensor data will be refreshed the next time the device wakes up", "info")
	state.pendingRefresh = true
}


/*
// helper to clean out unused state variables
private dbCleanUp() {
	state.remove("vibAct")
	state.remove("accelIgnore")
}
*/


// Parse incoming device messages to generate events
def parse(String description)
{
	def result = []
	try {
		def cmd = zwave.parse(description, commandClassVersions)
		if (description == "updated") {
			result << response(zwave.wakeUpV1.wakeUpIntervalSet(seconds: 7200, nodeid:zwaveHubNodeId))
			result << response(zwave.manufacturerSpecificV2.manufacturerSpecificGet())
		}

		if (cmd) {
			if( cmd.CMD == "8407" ) {
				result << response(zwave.batteryV1.batteryGet().format())
				result << new hubitat.device.HubAction(zwave.wakeUpV1.wakeUpNoMoreInformation().format())
			}
			result += zwaveEvent(cmd)
		}
		else {
			log("Unable to parse description: $description", "debug")
		}
	}
	catch(e) {
		log(e, "error")
	}
	if ( result[0] != null ) {
		log("Parse returned ${result}", "debug")
	}

	return result
}

def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd)
{
	def versions = [0x31: 2, 0x30: 1, 0x84: 1, 0x9C: 1, 0x70: 2]
	// def encapsulatedCommand = cmd.encapsulatedCommand(versions)
	def version = versions[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (!encapsulatedCommand) {
		log("Could not extract command from $cmd", "debug")
	} else {
		zwaveEvent(encapsulatedCommand)
	}
}

def createEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd, Map item1) {
	log("manufacturerId:   ${cmd.manufacturerId}", "debug")
	log("manufacturerName: ${cmd.manufacturerName}", "debug")
	log("productId:        ${cmd.productId}", "debug")
	log("productTypeId:    ${cmd.productTypeId}", "debug")
}

def createEvent(hubitat.zwave.commands.versionv1.VersionReport cmd, Map item1) {
	updateDataValue("applicationVersion", "${cmd.applicationVersion}")
	log("applicationVersion:      ${cmd.applicationVersion}", "debug")
	log("applicationSubVersion:   ${cmd.applicationSubVersion}", "debug")
	log("zWaveLibraryType:        ${cmd.zWaveLibraryType}", "debug")
	log("zWaveProtocolVersion:    ${cmd.zWaveProtocolVersion}", "debug")
	log("zWaveProtocolSubVersion: ${cmd.zWaveProtocolSubVersion}", "debug")
}

def createEvent(hubitat.zwave.commands.firmwareupdatemdv1.FirmwareMdReport cmd, Map item1) {
	log("checksum:       ${cmd.checksum}", "debug")
	log("firmwareId:     ${cmd.firmwareId}", "debug")
	log("manufacturerId: ${cmd.manufacturerId}", "debug")
}

def zwaveEvent(hubitat.zwave.commands.sensoralarmv1.SensorAlarmReport cmd)
{
	log("sensoralarmv1: acceleration", "trace")
	processAccelerationEvent(cmd.sensorState ? "active" : "inactive")
	return []
}

// Event Generation
def zwaveEvent(hubitat.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
	log("zwaveEvent: WakeUpNotification", , "trace")
	return [descriptionText: "${device.displayName} woke up", isStateChange: false]
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv2.SensorMultilevelReport cmd)
{
	log("zwaveEvent: SensorMultilevelReport V2", "trace")

	def map = [:]
	switch (cmd.sensorType) {
		case 1:
			// temperature
			def cmdScale = cmd.scale == 1 ? "F" : "C"
			map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
			map.unit = getTemperatureScale()
			map.name = "temperature"
			break;
		case 3:
			// luminance
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = "lux"
			map.name = "illuminance"
			break;
	}
	return map
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
	log("zwaveEvent: BatteryReport", "trace")
	def map = [:]
	map.name = "battery"
	map.value = cmd.batteryLevel > 0 ? cmd.batteryLevel.toString() : 1
	map.unit = "%"
	map.displayed = false
	return map
}


def zwaveEvent(hubitat.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
	log("zwaveEvent: SensorBinaryReport V1", "trace")
	sendMotionEvent(cmd.sensorValue ? "active" : "inactive")
	return []
}

def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
	log("SensorBinaryReport V2: ${cmd}", "trace")
	switch (cmd.sensorType) {
		case 8:
			processAccelerationEvent(cmd.sensorValue ? "active" : "inactive")
			break
		case 12:
			sendMotionEvent(cmd.sensorValue ? "active" : "inactive")
			break
		default:
			log("Unknown Sensor Type: ${cmd}", "debug")
	}
	return []
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	log("zwaveEvent: BasicSet V1 (motion)", "trace")
	sendMotionEvent(cmd.value ? "active" : "inactive")
	return []
}

def zwaveEvent(hubitat.zwave.Command cmd) {
	log("Catchall reached for cmd: ${cmd}", "trace")
	return []
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	log("${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd.configurationValue}'", "trace")
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	log("V1 ${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd.configurationValue}'", "trace")
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	def result = []

	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	log("msr: ${msr}", "trace")
	updateDataValue("MSR", msr)

	if ( msr == "010F-0800-2001" ) { //this is the msr and device type for the fibaro motion sensor
		configure()
	}

	result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
	return result
}

private sendMotionEvent(value) {
	log("motion ${value}", "info")
	sendEvent(getEventMap("motion", value))
}

private processAccelerationEvent(value) {
	String valVib = device.currentValue("vibration")
	if (value == "inactive") {
		if (vibrationReportInterval == 0) {  //disabled
			sendVibrationEvent(value)
		} else {
			// delay issuing a vibration inactive
			if (state.vibDelay == null || !state.vibDelay)
			{
				state.vibDelay = true
				runIn(vibrationReportInterval, 'delayedVibrationInactive')
			}
			log("Suppressed: Vibration inactive event", "debug")
		}
		sendAccelerationEvent(value)
	} else {  // active
		if (state.vibDelay != null && state.vibDelay) {
			unschedule('delayedVibrationInactive')
			state.vibDelay = false
			log("Vibration detected within window", "debug")
		}
		if (valVib == "inactive") { // one event only
			sendVibrationEvent(value)
		} else {
			log("Suppressed: Vibration duplicate active event", "debug")
		}

		sendAccelerationEvent(value)
	}
}

// handler for scheduled runin to delay reporting inactive vibration
private delayedVibrationInactive() {
	sendVibrationEvent('inactive')
	state.vibDelay = false;
}

private sendAccelerationEvent(value) {
	log("acceleration ${value}", "info")
	sendEvent(getEventMap("acceleration", value))
}

private sendVibrationEvent(String value) {
	log("vibration ${value}", "info")
	sendEvent(getEventMap("vibration", value))
}

private getEventMap(name, value, unit=null, displayed=true) {
	def eventMap = [
			name: name,
			value: value,
			displayed: displayed,
			isStateChange: true
	]
	if (unit) {
		eventMap.unit = unit
	}
	return eventMap
}

private determineLogLevel(data) {
	switch (data?.toUpperCase()) {
		case "TRACE":
			return 0
			break
		case "DEBUG":
			return 1
			break
		case "INFO":
			return 2
			break
		case "WARN":
			return 3
			break
		case "ERROR":
			return 4
			break
		default:
			return 1
	}
}

def log(Object data, String type) {
	data = "-- ${device.label} -- ${data ?: ''}"

	if (determineLogLevel(type) >= determineLogLevel(settings?.logging ?: "INFO")) {
		switch (type?.toUpperCase()) {
			case "TRACE":
				log.trace "${data}"
				break
			case "DEBUG":
				log.debug "${data}"
				break
			case "INFO":
				log.info "${data}"
				break
			case "WARN":
				log.warn "${data}"
				break
			case "ERROR":
				log.error "${data}"
				break
			default:
				log.error("-- ${device.label} -- Invalid Log Setting")
		}
	}
}
