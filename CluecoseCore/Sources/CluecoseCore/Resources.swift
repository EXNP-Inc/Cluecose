//
//  Resources.swift
//  CluecoseCore
//
//  Public access to the target's resource bundle. SwiftPM only generates
//  `Bundle.module` for targets that declare resources, so the test target
//  (which declares none) reaches the shared fixtures through this accessor.
//

import Foundation

public enum CluecoseCoreResources {
    /// The bundle containing `food/prompt.txt` and the golden fixtures in
    /// `food/tests/*.json`.
    public static let bundle: Bundle = Bundle.module
}
