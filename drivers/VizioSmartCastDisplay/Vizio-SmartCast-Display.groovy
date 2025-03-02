/**
 *  Vizio SmartCast Display Driver
 *
 *  Copyright 2020 Mike Cerwin
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
 *  Change History:
 *
 *    Date        Who             What
 *    ----        ---             ----
 *    2020-01-28  Mike Cerwin     v0.01 - Initial Release
 *    2020-02-21  Mike Cerwin     v0.02 - Added Input Child Device Functionality
 *    2021-03-03  Karl Lohner     Update various methods to iterate over responses, logging changes, modify children input naming
 *
 *  See https://github.com/exiva/Vizio_SmartCast_API for more API info
 */
metadata {
    definition (name: "Vizio SmartCast Display", namespace: "DixieChckn", author: "Mike Cerwin", , importUrl: "https://raw.githubusercontent.com/DixieChckn/Hubitat/master/drivers/VizioSmartCastDisplay/Vizio-SmartCast-Display.groovy") {
        
        capability "Actuator"
        capability "TV"
        capability "Switch"
        capability "AudioVolume"
        capability "Refresh"
        
        attribute "mute", "bool"
        attribute "volume", "string"
        attribute "channel", "string"
        attribute "input", "string"

        command "pair"
        command "completePairing", ["pin"]

 }

preferences {
    section("Settings") {
        input(name: "deviceIp", type: "string", title:"SmartCast IP Address", description: "", defaultValue: "192.168.1.1", required: true)
		input(name: "devicePort", type: "string", title:"SmartCast Port", description: "", defaultValue: "7345", required: true)
        input(name: "pairingId", type: "int", title:"Pairing ID", description: "Hub ID for Pairing", defaultValue: "123456789", required: true)
        input(name: "pairingName", type: "string", title:"Pairing Name", description: "Hub Name for Pairing", defaultValue: "Hubitat", required: true)
        input(name: "createChildDevs", type: "bool", title: "Create Input child devices", defaultValue: false)
        input(name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false)
        input(name: "pairEnable", type: "bool", title: "Enable pairing", defaultValue: true)
     } 
    }
}
    

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    state.deviceIp = deviceIp
    state.devicePort = devicePort
    if (logEnable){runIn(1800, logsOff)}
    if (createChildDevs) createChildDevices()
}

def parse(String description) {
    if (logEnable) log.debug(description)
}

def pair() { 
    
    if (pairEnable){
        
       if (logEnable) log.debug "Sending Pairing Command To [${deviceIp}:${devicePort}]"
        
        //Build Pairing Parameters
        def paramsForPairing =[
          uri: "https://${deviceIp}:${devicePort}",
          path: "/pairing/start", 
	      contentType: "application/json",
          body: "{\"DEVICE_ID\":\"${pairingId}\",\"DEVICE_NAME\":\"${pairingName}\"}",
          ignoreSSLIssues: true
          ]
        
        if(logEnable)log.debug "pair Request JSON: ${paramsForPairing}"
        
        //Send Pairing Request
        try {
            httpPut(paramsForPairing) { resp ->
                if (resp.success) {
                    state.pairingToken = resp.data.ITEM.PAIRING_REQ_TOKEN
                }
                
                if (logEnable) log.debug "pair Response JSON: ${resp.data}"
            }
        } catch (Exception e) {
            log.warn "pair Command Failed: ${e.message}"
        }  
    }
}

def completePairing(pin) {
    
    if (pairEnable){
        if (logEnable){ log.debug "Sending Pairing Completion Command To [${deviceIp}:${devicePort}]"
        log.debug "Pairing PIN: ${pin}"
        log.debug "Pairing Token: ${pairingToken}"}
        
        //Build Pairing Completion Request Parameters
        def paramsForPairingComp =[
          uri: "https://${deviceIp}:${devicePort}",
          path: "/pairing/pair", 
	      contentType: "application/json",
          body: "{\"DEVICE_ID\": \"${pairingId}\",\"CHALLENGE_TYPE\": 1,\"RESPONSE_VALUE\": \"${pin}\",\"PAIRING_REQ_TOKEN\": ${state.pairingToken}}",
          ignoreSSLIssues: true
          ]
        
        if(logEnable)log.debug "completePairing Request JSON: ${paramsForPairingComp}"
        
        
        //Send Pairing Completion COmmand
        try {
            httpPut(paramsForPairingComp) { resp ->
                if (resp.success) {
                state.authCode = resp.data.ITEM.AUTH_TOKEN
                }
                
                if(logEnable)log.debug "completePairing Response JSON: ${resp.data}"

            }
        } catch (Exception e) {
            log.warn "completePairing Command Failed: ${e.message}"
        }
    }
}

def on() {

    if (logEnable) log.debug "Sending Power On Command To [${deviceIp}:${devicePort}]"
      
        //Build Power On Parameters
        def paramsForOn =[
          uri: "https://${deviceIp}:${devicePort}",
          path: "/key_command/",
          headers: ["AUTH": "${state.authCode}"],  
	      contentType: "application/json",
          body: "{\"KEYLIST\":[{\"CODESET\": 11, \"CODE\": 1, \"ACTION\": \"KEYPRESS\"}]}",
          ignoreSSLIssues: true
          ]
    
        if(logEnable)log.debug "on Request JSON: ${paramsForOn}"
    
    //Send Power On Command
    try {
        httpPut(paramsForOn) { resp ->
            if (resp.success) {
                sendEvent(name: "switch", value: "on", isStateChange: true)
            }
            
            if (logEnable)log.debug "on Response JSON: ${resp.data}"
        }
     } catch (Exception e) {
        log.warn "on Command Failed: ${e.message}"
   }
}

def off() {
    
    if (logEnable) log.debug "Sending Power Off Command to [${deviceIp}:${devicePort}]"
        
        //Build Power Off Parameters
        def paramsForOff =[
          uri: "https://${deviceIp}:${devicePort}",
          path: "/key_command/",
          headers: ["AUTH": "${state.authCode}"],  
	      contentType: "application/json",
          body: "{\"KEYLIST\":[{\"CODESET\": 11, \"CODE\": 0, \"ACTION\": \"KEYPRESS\"}]}",
          ignoreSSLIssues: true
          ]
    
    if(logEnable)log.debug "off Request JSON: ${paramsForOff}"
    
    //Send Power Off Command
    try {
        httpPut(paramsForOff) { resp ->
            if (resp.success) {
                sendEvent(name: "switch", value: "off", isStateChange: true)
            }
            
            if (logEnable)log.debug "off Response JSON: ${resp.data}"
        }
     } catch (Exception e) {
        log.warn "off Command Failed: ${e.message}"
   }
}

def channelUp() {
    
    if (logEnable) log.debug "Sending Channel Up Command to [${deviceIp}:${devicePort}]"
        
        //Build Channel Up Parameters
        def paramsForCu =[
          uri: "https://${deviceIp}:${devicePort}",
          path: "/key_command/",
          headers: ["AUTH": "${state.authCode}"],  
	      contentType: "application/json",
          body: "{\"KEYLIST\":[{\"CODESET\": 8, \"CODE\": 1, \"ACTION\": \"KEYPRESS\"}]}",
          ignoreSSLIssues: true
          ]
    
        if(logEnable)log.debug "channelUp Request JSON: ${paramsForCu}"
    
    //Send Channel Up Command
    try {
        httpPut(paramsForCu) { resp ->
            if (resp.success) {
            }
            
            if (logEnable)log.debug "channelUp Response JSON: ${resp.data}"
        }
     } catch (Exception e) {
        log.warn "channelUp Command Failed: ${e.message}"
   }
}

def channelDown() {
    
    if (logEnable) log.debug "Sending Channel Down Command to [${deviceIp}:${devicePort}]"
        
        //Build Channel Down Parameters
        def paramsForCd =[
          uri: "https://${deviceIp}:${devicePort}",
          path: "/key_command/",
          headers: ["AUTH": "${state.authCode}"],  
	      contentType: "application/json",
          body: "{\"KEYLIST\":[{\"CODESET\": 8, \"CODE\": 0, \"ACTION\": \"KEYPRESS\"}]}",
          ignoreSSLIssues: true
          ]
    
        if(logEnable)log.debug "channelDown Request JSON: ${paramsForCd}"
    
    //Send Channel Down Command
    try {
        httpPut(paramsForCd) { resp ->
            if (resp.success) {
            }
            
            if (logEnable)log.debug "channelDown Response JSON: ${resp.data}"
        }
     } catch (Exception e) {
        log.warn "channelDown Command Failed: ${e.message}"
   }
}

def volumeUp() {
    
    if (logEnable) log.debug "Sending Volume Up Command to [${deviceIp}:${devicePort}]"
        
        //Build Volume Up Parameters
        def paramsForVu =[
          uri: "https://${deviceIp}:${devicePort}",
          path: "/key_command/",
          headers: ["AUTH": "${state.authCode}"],  
	      contentType: "application/json",
          body: "{\"KEYLIST\":[{\"CODESET\": 5, \"CODE\": 1, \"ACTION\": \"KEYPRESS\"}]}",
          ignoreSSLIssues: true
          ]
    
    if(logEnable)log.debug "VolumeUp Request JSON: ${paramsForVu}"
    
    //Send Volume Up Command
    try {
        httpPut(paramsForVu) { resp ->
            if (resp.success) { 
                runInMillis(900,refreshVol)
            }
            
            if (logEnable)log.debug "volumeUp Response JSON: ${resp.data}"
        }
     } catch (Exception e) {
        log.warn "Volume Up Command Failed: ${e.message}"
   }


}


def volumeDown() {
    
    if (logEnable) log.debug "Sending Volume Down Command to [${deviceIp}:${devicePort}]"
        
        //Build Volume Down Parameters
        def paramsForVd =[
          uri: "https://${deviceIp}:${devicePort}",
          path: "/key_command/",
          headers: ["AUTH": "${state.authCode}"],  
	      contentType: "application/json",
          body: "{\"KEYLIST\":[{\"CODESET\": 5, \"CODE\": 0, \"ACTION\": \"KEYPRESS\"}]}",
          ignoreSSLIssues: true
          ]
    
   if(logEnable)log.debug "VolumeDown Request JSON: ${paramsForVd}"    
    
    //Send Volume Down Command
    try {
        httpPut(paramsForVd) { resp ->
            if (resp.success) { 
                runInMillis(900,refreshVol)
            }
            
            if (logEnable)log.debug "volumeDown Response JSON: ${resp.data}"
        }
     } catch (Exception e) {
        log.warn "Volume Down Command Failed: ${e.message}"
   }
}

def setVolume(volumelevel) {

    if (logEnable) log.debug "Requesting Volume Status from [${deviceIp}:${devicePort}]"

    //Build Volume Status Request Parameters
    def volRequestParams = [ 
        uri: "https://${deviceIp}:${devicePort}",
        path: "/menu_native/dynamic/tv_settings/audio",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: ["AUTH": "${state.authCode}"],
        ignoreSSLIssues: true 
    ]

    if(logEnable)log.debug "volumeStatus Request JSON: ${paramsForVd}"

    try{
        //Send Volume Status Request
        httpGet(volRequestParams) { resp ->
            if (resp.success) {
                if (logEnable) {
                    log.debug "Refresh Audio Status Response: ${resp.data}"
                }
                List seqList = resp.data.ITEMS
                // log.debug "seqList: ${seqList}"
                if(seqList?.size()) {
                    seqList.each {
                        // log.debug "${it.CNAME}: ${it.VALUE}"
                        if(it.CNAME == "volume") {
                            volHash = it.HASHVAL
                            if(logEnable){ 
                                log.debug "Volume Hash Value: ${volHash}"
                            }

                        }
                    }
                }
                if(logEnable){
                    log.debug "Current Volume: ${device.currentValue("volume")}"
                }
            }
        }
    } catch (Exception e) {
        log.warn "Volume Status Request Failed: ${e.message}"
    }

    if (logEnable) log.debug "Sending Set Volume to [${deviceIp}:${devicePort}]"

    //Build Set Volume Parameters
    def paramsForSetVol =[
        uri: "https://${deviceIp}:${devicePort}",
        path: "/menu_native/dynamic/tv_settings/audio/volume",
        headers: ["AUTH": "${state.authCode}"],  
        contentType: "application/json",
        body: "{\"REQUEST\": \"MODIFY\", \"VALUE\": ${volumelevel}, \"HASHVAL\": ${volHash}}",
        ignoreSSLIssues: true    
    ]

    if(logEnable)log.debug "setVolume Request JSON: ${paramsForSetVol}"

    //Send Set Volume Command
    try {
        httpPut(paramsForSetVol) { resp ->

            if (logEnable) log.debug "volumeSet Response JSON: ${resp.data}"
        }
    } catch (Exception e) {
        log.warn "Set Volume Command Failed: ${e.message}"
    }
}

def mute() {
    
    if (logEnable) log.debug "Requesting Mute Status from [${deviceIp}:${devicePort}]"
    
    //Build Mute Status Request parameters
    def muteRequestParams = [ 
        uri: "https://${deviceIp}:${devicePort}",
        path: "/menu_native/dynamic/tv_settings/audio",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: ["AUTH": "${state.authCode}"],
        ignoreSSLIssues: true 
    ]

    if (logEnable) log.debug "mute Status Request JSON: ${muteRequestParams}"

    try {
        //Send Mute Status Request
        httpGet(muteRequestParams) { resp ->
            if (resp.success) {
                if (logEnable) {
                    log.debug "Mute Status Response: ${resp.data}"
                }
                List seqList = resp.data.ITEMS
                // log.debug "seqList: ${seqList}"
                if(seqList?.size()) {
                    seqList.each {
                        // log.debug "${it.CNAME}: ${it.VALUE}"
                        if(it.CNAME == "mute") {
                            muteStatus = it.VALUE.toLowerCase()
                        }
                    }
                }
                if (logEnable) log.debug "MuteStatus: ${muteStatus}"                                     
            }
        }
    } catch (Exception e) {
        log.warn "Mute Status Request Failed: ${e.message}"
    }
     
    if (logEnable) log.debug "Sending Mute Command to [${deviceIp}:${devicePort}]"
    
    //Build Mute Parameters
    def paramsForMute =[
        uri: "https://${deviceIp}:${devicePort}",
        path: "/key_command/",
        headers: ["AUTH": "${state.authCode}"],  
        contentType: "application/json",
        body: "{\"KEYLIST\":[{\"CODESET\": 5, \"CODE\": 3, \"ACTION\": \"KEYPRESS\"}]}",
        ignoreSSLIssues: true
    ]

    if(logEnable)log.debug "mute Request JSON: ${paramsForMute}"
    
    //Send Mute Command
    if (muteStatus == "off") {
    try {
        httpPut(paramsForMute) { resp ->
            if (resp.success) {
                sendEvent(name: "mute", value: "on", isStateChange: true)
            }
            
            if (logEnable) log.debug "mute Response JSON: ${resp.data}"
        }
      } catch (Exception e) {
        log.warn "Mute Command Failed: ${e.message}"
    }
  }
}

def unmute() {

    if (logEnable) log.debug "Sending Unmute Status request to [${deviceIp}:${devicePort}]"

    //Build Unmute Status Request parameters
    def unmuteRequestParams = [ 
        uri: "https://${deviceIp}:${devicePort}",
        path: "/menu_native/dynamic/tv_settings/audio",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: ["AUTH": "${state.authCode}"],
        ignoreSSLIssues: true 
    ]

    if(logEnable)log.debug "unmute Status Request JSON: ${muteRequestParams}"    

    try{
        //Send Unmute Status Request
        httpGet(unmuteRequestParams) { resp ->
            if (resp.success) {
                if (logEnable) {
                    log.debug "Unmute Status Response: ${resp.data}"
                }
                List seqList = resp.data.ITEMS
                // log.debug "seqList: ${seqList}"
                if(seqList?.size()) {
                    seqList.each {
                        // log.debug "${it.CNAME}: ${it.VALUE}"
                        if(it.CNAME == "mute") {
                            muteStatus = it.VALUE.toLowerCase()
                        }
                    }
                }
                if (logEnable) log.debug "MuteStatus: ${muteStatus}"                                     
            }
        }
    } catch (Exception e) {
        log.warn "Unmute Status Request Failed: ${e.message}"
    }

    if (logEnable) log.debug "Sending Unmute Command to [${deviceIp}:${devicePort}]"

    //Build Unmute Parameters
    def paramsForUnmute =[
        uri: "https://${deviceIp}:${devicePort}",
        path: "/key_command/",
        headers: ["AUTH": "${state.authCode}"],  
        contentType: "application/json",
        body: "{\"KEYLIST\":[{\"CODESET\": 5, \"CODE\": 2, \"ACTION\": \"KEYPRESS\"}]}",
        ignoreSSLIssues: true
    ]

    //Send Unmute Command
    if (muteStatus == "on") {
        try {
            httpPut(paramsForUnmute) { resp ->
                if (resp.success) {
                    sendEvent(name: "mute", value: "off", isStateChange: true)
                }
            }
        } catch (Exception e) {
            log.warn "unmute Command Failed: ${e.message}"
        }
    }
}

def refresh() {

    if (logEnable) log.debug "Sending Refresh Request to [${deviceIp}:${devicePort}]"
    
    //Build Status Request Parameters - Audio
    def audStatusRequestParams = [ 
        uri: "https://${deviceIp}:${devicePort}",
        path: "/menu_native/dynamic/tv_settings/audio",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: ["AUTH": "${state.authCode}"],
        ignoreSSLIssues: true 
    ]
    try {
        //Send Status Request
        httpGet(audStatusRequestParams) { resp ->
            if (resp.success) {
                if (logEnable) {
                    log.debug "Refresh Audio Status Response: ${resp.data}"
                }
                List seqList = resp.data.ITEMS
                // log.debug "seqList: ${seqList}"
                if(seqList?.size()) {
                    seqList.each {
                        // log.debug "${it.CNAME}: ${it.VALUE}"
                        if(it.CNAME == "volume") {
                            if(device.currentValue("volume") != it.VALUE){
                                sendEvent(name: "volume", value: "${it.VALUE}", isStateChange: true)
                            }
                        }
                        if(it.CNAME == "mute") {
                            if(device.currentValue("mute") != it.VALUE.toLowerCase()){
                                sendEvent(name: "mute", value: "${it.VALUE.toLowerCase()}", isStateChange: true)
                            }
                        }
                    }
                }
                if(logEnable){
                    log.debug "Current Volume: ${device.currentValue("volume")}"
                    log.debug "Current Mute State: ${device.currentValue("mute")}"
                }
            }
        }
    } catch (Exception e) {
        log.warn "Audio Status Request Failed: ${e.message}"
    }
        
    //Build Power Status Request Parameters
    def pwrStatusRequestParams = [ 
        uri: "https://${deviceIp}:${devicePort}",
        path: "/state/device/power_mode",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: ["AUTH": "${state.authCode}"],
        ignoreSSLIssues: true
    ]
    
    //Send Power Status Request
    try {
        httpGet(pwrStatusRequestParams) { resp ->
            if (resp.success) {
                if (logEnable) {
                    log.debug "Refresh Power Status Response: ${resp.data}"
                }
                List seqList = resp.data.ITEMS
                // log.debug "seqList: ${seqList}"
                if(seqList?.size()) {
                    seqList.each {
                        // log.debug "${it.CNAME}: ${it.VALUE}"
                        if(it.CNAME == "power_mode") {
                            if (it.VALUE == 0 && device.currentValue("switch") == "on") {
                                sendEvent(name: "switch", value: "off", isStateChange: true)
                            }
                            else 
                            if (it.VALUE == 1 && device.currentValue("switch") == "off") {
                                sendEvent(name: "switch", value: "on", isStateChange: true)
                            }
                        }
                    }
                }
            }
            if (logEnable) {
                log.debug "Power State: ${device.currentValue("switch")}"
            }
        }
    } catch (Exception e) {
        log.warn "Power Status Request Failed: ${e.message}"
    }

    //Build Input List Request Parameters
    def inputListRequestParams = [ 
        uri: "https://${deviceIp}:${devicePort}",
        path: "/menu_native/dynamic/tv_settings/devices/name_input",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: ["AUTH": "${state.authCode}"],
        ignoreSSLIssues: true 
    ]
    try {
        //Send  Input List Request
        httpGet(inputListRequestParams) { resp ->
            if (resp.success) {
                if (logEnable) {
                    log.debug "Refresh Name Input Status Response: ${resp.data}"
                }
                List seqList = resp.data.ITEMS
                // log.debug "seqList: ${seqList}"
                if(seqList?.size()) {
                    seqList.each {
                        // log.debug "${it.CNAME}: ${it.VALUE}"
//                        if(it.CNAME == "current_input") {
//                            if(device.currentValue("input") != it.VALUE) {
//                                sendEvent(name: "input", value: "${it.VALUE}", isStateChange: true)
//                            }
//                        }
                    }
                }
            }
            if (logEnable) {
//                log.debug "Name Input: ${device.currentValue("input")}"
            }
        }
    } catch (Exception e) {
        log.warn "Current Input Status Request Failed: ${e.message}"
    }
    
    
    //Build Current Input Hash Request Parameters
    def currentInputRequestParams = [ 
        uri: "https://${deviceIp}:${devicePort}",
        path: "/menu_native/dynamic/tv_settings/devices/current_input",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: ["AUTH": "${state.authCode}"],
        ignoreSSLIssues: true 
    ]
    try {
        //Send Current Input Hash Request
        httpGet(currentInputRequestParams) { resp ->
            if (resp.success) {
                if (logEnable) {
                    log.debug "Refresh Current Input Status Response: ${resp.data}"
                }
                List seqList = resp.data.ITEMS
                // log.debug "seqList: ${seqList}"
                if(seqList?.size()) {
                    seqList.each {
                        // log.debug "${it.CNAME}: ${it.VALUE}"
                        if(it.CNAME == "current_input") {
                            if(device.currentValue("input") != it.VALUE) {
                                sendEvent(name: "input", value: "${it.VALUE}", isStateChange: true)
                            }
                        }
                    }
                }
            }
            if (logEnable) {
                log.debug "Current Input: ${device.currentValue("input")}"
            }
        }
    } catch (Exception e) {
        log.warn "Current Input Status Request Failed: ${e.message}"
    }
}

def refreshVol() {

    if (logEnable) log.debug "Sending Volume Refresh Request to [${deviceIp}:${devicePort}]"

    //Build Status Request Parameters - Audio
    def audStatusRequestParams = [ 
        uri: "https://${deviceIp}:${devicePort}",
        path: "/menu_native/dynamic/tv_settings/audio/volume",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: ["AUTH": "${state.authCode}"],
        ignoreSSLIssues: true 
    ]
    try {
        //Send Status Request - Audio
        httpGet(audStatusRequestParams) { resp ->
            if (resp.success) {
                if (logEnable) {
                    log.debug "Refresh Audio Status Response: ${resp.data}"
                }
                List seqList = resp.data.ITEMS
                // log.debug "seqList: ${seqList}"
                if(seqList?.size()) {
                    seqList.each {
                        // log.debug "${it.CNAME}: ${it.VALUE}"
                        if(it.CNAME == "volume") {
                            if(device.currentValue("volume") != it.VALUE){
                                sendEvent(name: "volume", value: "${it.VALUE}", isStateChange: true)
                            }
                        }
                    }
                }
                if(logEnable){
                    log.debug "Current Volume: ${device.currentValue("volume")}"
                }
            }
        }
    } catch (Exception e) {
        log.warn "Audio Status Request Failed: ${e.message}"
    } 
}


def createChildDevices() {
    
    if (logEnable) log.debug "Sending Input List Request to [${deviceIp}:${devicePort}]"

    //Build Input List Request Parameters
    def inputListRequestParams = [ 
        uri: "https://${deviceIp}:${devicePort}",
        path: "/menu_native/dynamic/tv_settings/devices/name_input",
        contentType: "application/json",
        requestContentType: "application/json",
        headers: ["AUTH": "${state.authCode}"],
        ignoreSSLIssues: true 
    ]
    try{
        //Send  Input List Request
        httpGet(inputListRequestParams) { resp ->
            if (resp.success) {
                if (logEnable) {
                    log.debug "Refresh Name Input Status Response: ${resp.data}"
                }
                List seqList = resp.data.ITEMS
                // Example item which has been renamed to "Cable TV" using the TV remote
                //    [
                //        "HASHVAL":1171194620,
                //        "CNAME":hdmi1,
                //        "NAME":HDMI-1,
                //        "TYPE":T_DEVICE_V1,
                //        "ENABLED":false,
                //        "VALUE":[
                //            "NAME":"Cable TV",
                //            "METADATA":
                //        ]
                //    ],                

                // log.debug "seqList: ${seqList}"
                if(seqList?.size()) {
                    seqList.each {
                        if (logEnable) {
                            log.debug "Input: ${it}"
                        }
                        deviceNetworkID = "${device.deviceNetworkId}-input-${it.NAME}"
                        if (!getChildDevice(deviceNetworkID)) {
                            try {
                                def dev = addChildDevice("Vizio SmartCast Input", deviceNetworkID,
                                                         ["label": "${device.displayName} - ${it.NAME} (${it.VALUE.NAME})",
                                                          "isComponent": false])

                            } catch (Exception e) {
                                log.error "Error creating SmartCast Input child device: $e"
                            } 
                        }
                    }
                } 
            }
        }
    } catch (Exception e) {
        log.warn "inputList Request Failed: ${e.message}"
    } 
}
