//  Converted to Swift 5.3 by Swiftify v5.3.19197 - https://swiftify.com/
//
//  NullSafe.swift
//
//  Version 1.2.3
//
//  Created by Nick Lockwood on 19/12/2012.
//  Copyright 2012 Charcoal Design
//
//  Distributed under the permissive zlib License
//  Get the latest version from here:
//
//  https://github.com/nicklockwood/NullSafe
//
//  This software is provided 'as-is', without any express or implied
//  warranty.  In no event will the authors be held liable for any damages
//  arising from the use of this software.
//
//  Permission is granted to anyone to use this software for any purpose,
//  including commercial applications, and to alter it and redistribute it
//  freely, subject to the following restrictions:
//
//  1. The origin of this software must not be misrepresented; you must not
//  claim that you wrote the original software. If you use this software
//  in a product, an acknowledgment in the product documentation would be
//  appreciated but is not required.
//
//  2. Altered source versions must be plainly marked as such, and must not be
//  misrepresented as being the original software.
//
//  3. This notice may not be removed or altered from any source distribution.
//

import Foundation
import ObjectiveC

#if !NULLSAFE_ENABLED
let NULLSAFE_ENABLED = 1
#endif


//#pragma GCC diagnostic ignored "-Wgnu-conditional-omitted-operand"


#if NULLSAFE_ENABLED
    private var classList: Set<Class>? = nil
private var signatureCache: [String : Any?]? = nil


private func cacheSignatures() {
    classList = []
    signatureCache = [:]

    //get class list
    var numClasses = Int(objc_getClassList(nil, 0))
    let classes: AnyClass? = malloc(MemoryLayout<AnyClass>.size * UInt(numClasses)) as? AnyClass
    numClasses = Int(objc_getClassList(&classes, Int32(numClasses)))

    //add to list for checking
    for i in 0..<numClasses {
        //determine if class has a superclass
        let someClass = classes?[i]
        var superclass: AnyClass? = class_getSuperclass(someClass)
        while superclass {
            if superclass == NSObject.self {
                classList?.insert(someClass)
                classList?.remove(someClass?.superclass())
                break
            }
            superclass = class_getSuperclass(superclass)
        }
    }

    //free class list
    free(classes)
}

extension NSNull {
    func methodSignature(for selector: Selector) -> NSMethodSignature? {
        //look up method signature
        var signature = super.methodSignature(for: selector)
        if signature == nil {
            //check implementation cache first
            let selectorString = NSStringFromSelector(selector)
            signature = signatureCache?[selectorString] as? NSMethodSignature
            if signature == nil {
                let lockQueue = DispatchQueue(label: "NSNull.self")
                lockQueue.sync {
                    //check again, in case it was resolved while we were waitimg
                    signature = signatureCache?[selectorString] as? NSMethodSignature
                    if signature == nil {
                        //not supported by NSNull, search other classes
                        if signatureCache == nil {
                            if Thread.isMainThread {
                                cacheSignatures()
                            } else {
                                DispatchQueue.main.sync(execute: {
                                    cacheSignatures()
                                })
                            }
                        }

                        //find implementation
                        for someClass in classList ?? [] {
                            if someClass.instancesRespond(to: selector) {
                                signature = someClass.instanceMethodSignature(for: selector)
                                break
                            }
                        }

                        //cache for next time
                        signatureCache?[selectorString] = signature ?? NSNull()
                    } else if signature is NSNull {
                        signature = nil
                    }
                }
            }
        }
        return signature
    }

    func forwardInvocation(_ invocation: NSInvocation?) {
        invocation?.target = nil
        invocation?.invoke()
    }
#endif
}