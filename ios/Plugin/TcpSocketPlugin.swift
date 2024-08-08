import Foundation
import Capacitor
import SwiftSocket

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(TcpSocketPlugin)
public class TcpSocketPlugin: CAPPlugin {
    var clients: [TCPClient] = []
    
    @objc func connect(_ call: CAPPluginCall) {
        guard let ip = call.getString("ipAddress") else {
            call.reject("Must provide ip address to connect")
            return
        }
        let port = Int32(call.getInt("port", 9100))
        
        let client = TCPClient(address: ip, port: port)
        switch client.connect(timeout: 10) {
          case .success:
            clients.append(client);
            call.resolve(["client": clients.count - 1])
          case .failure(let error):
            call.reject(error.localizedDescription)
        }
    }

    
    @objc func send(_ call: CAPPluginCall) {
        // 获取客户端索引
        let clientIndex = call.getInt("client", -1)
        guard clientIndex >= 0, clientIndex < clients.count else {
            call.reject("No client specified or client index out of range")
            return
        }
        let client = clients[clientIndex]

        // 获取数据类型和数据
        let dataType = call.getString("encoding", "utf8")
        guard let dataString = call.getString("data") else {
            call.reject("No data provided")
            return
        }
        
        print("dataType", dataType)
        print("dataString", dataString)
        
        // 存储转换后的字节数组
        var byteArray = [UInt8]()
        
        // 根据数据类型处理数据
        switch dataType {
        case "utf8":
            byteArray = dataString.utf8.map { $0 }
        case "base64":
            if let decodedData = Data(base64Encoded: dataString) {
                byteArray = [UInt8](decodedData)
            } else {
                call.reject("Invalid Base64 string")
                return
            }
        case "hex":
            let hexString = dataString
            if hexString.count % 2 != 0 {
                call.reject("Invalid hex string length")
                return
            }
            byteArray = stride(from: 0, to: hexString.count, by: 2).compactMap { index in
                let start = hexString.index(hexString.startIndex, offsetBy: index)
                let end = hexString.index(start, offsetBy: 2)
                let byteString = String(hexString[start..<end])
                return UInt8(byteString, radix: 16)
            }
            if byteArray.count * 2 != hexString.count {
                call.reject("Invalid hex string format")
                return
            }
        default:
            call.reject("Unsupported data type")
            return
        }
        
        // 发送数据并处理结果
        switch client.send(data: byteArray) {
        case .success:
            call.resolve()
        case .failure(let error):
            call.reject(error.localizedDescription)
        }
    }
    
    
    @objc func read(_ call: CAPPluginCall) {
        let clientIndex = call.getInt("client", -1)
        if (clientIndex == -1)    {
            call.reject("No client specified")
        }
        let client = clients[clientIndex]
        
        let expectLen = call.getInt("expectLen", 1024)
        let timeout = call.getInt("timeout", 10)
        
        guard let response = client.read(expectLen, timeout: timeout),
                let data = String(bytes: response, encoding: .utf8) else {
            call.resolve(["result": ""])
            return
        }
        
        call.resolve(["result": data])
    }
    
    @objc func disconnect(_ call: CAPPluginCall) {
        let clientIndex = call.getInt("client") ?? -1
        if (clientIndex == -1)  {
            call.reject("No client specified")
        }
        if (clients.indices.contains(clientIndex))   {
            clients[clientIndex].close()
        }
        call.resolve(["client": clientIndex])
    }
}
