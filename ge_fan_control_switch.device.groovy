/**
 *  Aeon HEMv2
 *
 *  Copyright 2014 Barry A. Burke
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 *  Aeon Home Energy Meter v2 (US)
 *
 *  Author: Barry A. Burke
 *  Contributors: Brock Haymond: UI updates
 *
 *  Genesys: Based off of Aeon Smart Meter Code sample provided by SmartThings (2013-05-30). Built on US model
 *           may also work on international versions (currently reports total values only)
 *
 *  History:
 *      
 *  2014-06-13: Massive OverHaul
 *              - Fixed Configuration (original had byte order of bitstrings backwards
 *              - Increased reporting frequency to 10s - note that values won't report unless they change
 *                (they will also report if they exceed limits defined in the settings - currently just using
 *                the defaults).
 *              - Added support for Volts & Amps monitoring (was only Power and Energy)
 *              - Added flexible tile display. Currently only used to show High and Low values since last
 *                reset (with time stamps). 
 *              - All tiles are attributes, so that their values are preserved when you're not 'watching' the
 *                meter display
 *              - Values are formatted to Strings in zwaveEvent parser so that we don't lose decimal values 
 *                in the tile label display conversion
 *              - Updated fingerprint to match Aeon Home Energy Monitor v2 deviceId & clusters
 *              - Added colors for Watts and Amps display
 *              - Changed time format to 24 hour
 *  2014-06-17: Tile Tweaks
 *              - Reworked "decorations:" - current values are no longer "flat"
 *              - Added colors to current Watts (0-18000) & Amps (0-150)
 *              - Changed all colors to use same blue-green-orange-red as standard ST temperature guages
 *  2014-06-18: Cost calculations
 *              - Added $/kWh preference
 *  2014-09-07: Bug fix & Cleanup
 *              - Fixed "Unexpected Error" on Refresh tile - (added Refresh Capability)
 *              - Cleaned up low values - reset to ridiculously high value instead of null
 *              - Added poll() command/capability (just does a refresh)
 *              
 *  2014-09-19   GUI Tweaks, HEM v1 alterations (from Brock Haymond)
 *              - Reworked all tiles for look, color, text formatting, & readability
 */

metadata {

	definition (name: "My Aeon Home Energy Monitor v1", namespace: "jscgs350", author: "Barry A. Burke") 
    {
        capability "Energy Meter"
        capability "Power Meter"
        capability "Configuration"
        capability "Sensor"
        capability "Refresh"
        capability "Polling"
        capability "Battery"
        
        attribute "energy", "string"
        attribute "energyDisp", "string"
        attribute "energyOne", "string"
        attribute "energyTwo", "string"
        
        attribute "power", "string"
        attribute "powerDisp", "string"
        attribute "powerOne", "string"
        attribute "powerTwo", "string"
        
        command "reset"
        command "configure"
        
        fingerprint deviceId: "0x2101", inClusters: " 0x70,0x31,0x72,0x86,0x32,0x80,0x85,0x60"

// v2        fingerprint deviceId: "0x3101", inClusters: "0x70,0x32,0x60,0x85,0x56,0x72,0x86"
    }

// tile definitions
    
    tiles {
    
// Watts row

        valueTile("powerDisp", "device.powerDisp", inactiveLabel: false, decoration: "flat") {
            state ("default", label:'${currentValue}')
        }
        
        valueTile("powerOne", "device.powerOne", inactiveLabel: false, decoration: "flat") {
            state("default", label:'${currentValue}')
        }
        
        valueTile("powerTwo", "device.powerTwo", inactiveLabel: false, decoration: "flat") {
            state("default", label:'${currentValue}')
        }

// Power row
    
        valueTile("energyDisp", "device.energyDisp", inactiveLabel: false, decoration: "flat") {
            state("default", label: '${currentValue}', backgroundColor:"#ffffff")
        }
        
        valueTile("energyOne", "device.energyOne", inactiveLabel: false, decoration: "flat") {
            state("default", label: '${currentValue}', backgroundColor:"#c3cb71")
        }        
        
        valueTile("energyTwo", "device.energyTwo", inactiveLabel: false, decoration: "flat") {
            state("default", label: '${currentValue}', backgroundColor:"#7FE45E")
        }
        
// Controls row
    

        standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Reset', action:"reset", icon:"st.secondary.refresh-icon"
		}
        standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("configure", "device.power", inactiveLabel: false, decoration: "flat") {
            state "configure", label:'', action:"configure", icon:"st.secondary.configure"
        }
        valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
            state "battery", label:'${currentValue}% battery', unit:""
        }


// TODO: Add configurable delay button - Cycle through 10s, 30s, 1m, 5m, 60m, off?

        main (["powerDisp"])
        details(["powerOne","powerDisp","powerTwo","reset","refresh", "configure"])
    	}
    
        preferences {
            input "kWhCost", "string", title: "\$/kWh (0.16)", defaultValue: "0.16" as String
        }
}


def parse(String description) {
    log.debug "Parse received ${description}"
    def result = null
    def cmd = zwave.parse(description, [0x31: 1, 0x32: 1, 0x60: 3])
    if (cmd) {
        result = createEvent(zwaveEvent(cmd))
    }
    if (result) log.debug "Parse returned ${result}"
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.meterv1.MeterReport cmd) {
    log.debug "zwaveEvent received ${cmd}"
    
    def dispValue
    def newValue
    def timeString = new Date().format("h:mm a", location.timeZone)
    
    if (cmd.meterType == 33) {
        if (cmd.scale == 0) {
            newValue = cmd.scaledMeterValue
            if (newValue != state.energyValue) {
                dispValue = String.format("%5.2f",newValue)+"\nkWh"
                sendEvent(name: "energyDisp", value: dispValue as String, unit: "")
                state.energyValue = newValue
                BigDecimal costDecimal = newValue * ( kWhCost as BigDecimal)
                def costDisplay = String.format("%3.2f",costDecimal)
                sendEvent(name: "energyTwo", value: "Cost\n\$${costDisplay}", unit: "")
                [name: "energy", value: newValue, unit: "kWh"]
            }
        } else if (cmd.scale == 1) {
            newValue = cmd.scaledMeterValue
            if (newValue != state.energyValue) {
                dispValue = String.format("%5.2f",newValue)+"\nkVAh"
                sendEvent(name: "energyDisp", value: dispValue as String, unit: "")
                state.energyValue = newValue
                [name: "energy", value: newValue, unit: "kVAh"]
            }
        }
        else if (cmd.scale==2) {                
            newValue = Math.round( cmd.scaledMeterValue )       // really not worth the hassle to show decimals for Watts
            if (newValue != state.powerValue) {
                dispValue = newValue+"w"
                sendEvent(name: "powerDisp", value: dispValue as String, unit: "")
                
                if (newValue < state.powerLow) {
                    dispValue = "Low: "+newValue+"w" //+timeString
                    sendEvent(name: "powerOne", value: dispValue as String, unit: "")
                    state.powerLow = newValue
                }
                if (newValue > state.powerHigh) {
                    dispValue = "High: "+newValue+"w" //+timeString
                    sendEvent(name: "powerTwo", value: dispValue as String, unit: "")
                    state.powerHigh = newValue
                }
                state.powerValue = newValue
                [name: "power", value: newValue, unit: "W"]
            }
        }
    }
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
    def map = [:]
    map.name = "battery"
    map.unit = "%"
    if (cmd.batteryLevel == 0xFF) {
        map.value = 1
        map.descriptionText = "${device.displayName} has a low battery"
        map.isStateChange = true
    } else {
        map.value = cmd.batteryLevel
    }
    log.debug map
    return map
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    // Handles all Z-Wave commands we aren't interested in
    log.debug "Unhandled event ${cmd}"
    [:]
}

def refresh() {
    delayBetween([
        zwave.meterV2.meterGet(scale: 0).format(),
        zwave.meterV2.meterGet(scale: 2).format()
    ])
}

def poll() {
    refresh()
}
    
def reset() {
    log.debug "${device.name} reset"

    state.powerHigh = 0
    state.powerLow = 99999
    
    def dateString = new Date().format("m/d/YY", location.timeZone)
    def timeString = new Date().format("h:mm a", location.timeZone)
    sendEvent(name: "energyOne", value: "Since\n"+dateString+"\n"+timeString, unit: "")
    sendEvent(name: "powerOne", value: "", unit: "")    
    sendEvent(name: "powerDisp", value: "", unit: "")    
    sendEvent(name: "energyDisp", value: "", unit: "")
    sendEvent(name: "energyTwo", value: "Cost\n--", unit: "")
    sendEvent(name: "powerTwo", value: "", unit: "")    
    
// No V1 available
    def cmd = delayBetween( [
        zwave.meterV2.meterReset().format(),
        zwave.meterV2.meterGet(scale: 0).format()
    ])
    
    cmd
}

def configure() {
    // TODO: Turn on reporting for each leg of power - display as alternate view (Currently those values are
    //       returned as zwaveEvents...they probably aren't implemented in the core Meter device yet.

    def cmd = delayBetween([
        zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: 1).format(),      // Enable selective reporting
        zwave.configurationV1.configurationSet(parameterNumber: 4, size: 2, scaledConfigurationValue: 50).format(),     // Don't send unless watts have increased by 50
        zwave.configurationV1.configurationSet(parameterNumber: 8, size: 2, scaledConfigurationValue: 10).format(),     // Or by 10% (these 3 are the default values
        zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 10).format(),   // Average Watts & Amps
        zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: 30).format(),   // Every 30 Seconds
        zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 4).format(),    // Average Voltage
        zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: 150).format(),  // every 2.5 minute
        zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 1).format(),    // Total kWh (cumulative)
        zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: 300).format()   // every 5 minutes
    ])
    log.debug cmd

    cmd
}