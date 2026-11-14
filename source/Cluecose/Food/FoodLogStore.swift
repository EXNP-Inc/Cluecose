//
//  FoodLogStore.swift
//  Cluecose
//
//  Persistence for the food log, mirroring the `LibreDirectReader` idiom:
//  a Codable model encoded with `JSONEncoder` and stored as `Data` in
//  `UserDefaults` under a stable key — the SAME key and JSON schema the
//  Android port writes (`cluecose.food.entries`), per the CluecoseCore
//  cross-platform contract.
//
//  • 30-day rolling retention: on load, entries older than 30 days are dropped
//    (and the pruned set is written back).
//  • Photos are stored as files in the app's Documents/`photos` directory with
//    a stable, predictable name (`{entryId}_{index}.jpg`) so `FoodEntry.
//    photoFileNames` (file names, not paths) stays portable and the entry's
//    JSON never balloons.
//

import Foundation
import CluecoseCore

final class FoodLogStore {

    /// The cross-platform storage key (identical on the Android port).
    static let entriesKey = "cluecose.food.entries"
    /// Rolling retention window: entries older than this are dropped on load.
    static let retentionDays = 30
    /// Subdirectory (inside Documents) holding the meal photo files.
    static let photosDirectoryName = "photos"

    /// Injectable for tests (a suite-scoped `UserDefaults`); production uses
    /// `.standard` — the same store the Android port reads.
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    // MARK: Photos directory

    /// The Documents/`photos` directory, created on demand.
    static var photosDirectory: URL {
        let dir = FileManager.default
            .urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(photosDirectoryName, isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    /// The URL of a stored photo file (the file may not exist yet).
    func photoURL(_ fileName: String) -> URL {
        Self.photosDirectory.appendingPathComponent(fileName)
    }

    // MARK: Entries

    /// All entries, newest first is the caller's concern; the store keeps the
    /// persisted order (insertion order), which is oldest -> newest.
    func all() -> [FoodEntry] {
        load()
    }

    /// Load entries from UserDefaults, applying the 30-day rolling retention
    /// (dropped entries are removed from disk too — their photos included).
    private func load() -> [FoodEntry] {
        guard let data = defaults.data(forKey: Self.entriesKey),
              let decoded = try? JSONDecoder().decode([FoodEntry].self, from: data) else {
            return []
        }
        let cutoff = Date().addingTimeInterval(-Double(Self.retentionDays) * 86_400)
        let kept = decoded.filter { $0.date >= cutoff }
        if kept.count != decoded.count {
            // Prune: drop the expired entries (and their photo files).
            for dropped in decoded where dropped.date < cutoff {
                deletePhotos(for: dropped.id)
            }
            save(kept)
        }
        return kept
    }

    /// Persist the full entry list (JSONEncoder -> Data, like LibreDirectReader).
    private func save(_ entries: [FoodEntry]) {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        defaults.set(data, forKey: Self.entriesKey)
    }

    /// Persist an already-loaded full list without re-reading the key
    /// (used by the model after a batch update such as insight recompute).
    func persistAll(_ entries: [FoodEntry]) {
        save(entries)
    }

    /// Insert a new entry (at the end = newest).
    func add(_ entry: FoodEntry) {
        var entries = load()
        entries.append(entry)
        save(entries)
    }

    /// Replace the entry with the same id (in place, keeping order).
    func update(_ entry: FoodEntry) {
        var entries = load()
        if let index = entries.firstIndex(where: { $0.id == entry.id }) {
            entries[index] = entry
        } else {
            entries.append(entry)
        }
        save(entries)
    }

    /// Remove an entry and its photo files.
    func delete(id: String) {
        var entries = load()
        if let index = entries.firstIndex(where: { $0.id == id }) {
            deletePhotos(for: entries[index].id)
            entries.remove(at: index)
            save(entries)
        }
    }

    /// Entries eaten on the same local-calendar day as `day`.
    func entries(on day: Date) -> [FoodEntry] {
        let calendar = Calendar.current
        return load().filter { calendar.isDate($0.date, inSameDayAs: day) }
    }

    /// Entries within the trailing `days` days (inclusive of today), oldest
    /// -> newest. Used for patterns + sleep correlation.
    func entries(inLast days: Int) -> [FoodEntry] {
        let cutoff = Date().addingTimeInterval(-Double(max(days, 1)) * 86_400)
        return load().filter { $0.date >= cutoff }
    }

    // MARK: Photos

    /// Write one photo file for an entry. Stable name `{entryId}_{index}.jpg`.
    /// Returns the stored file name (to put into `photoFileNames`).
    @discardableResult
    func savePhoto(_ data: Data, for entryId: String, index: Int) -> String {
        let name = "\(entryId)_\(index).jpg"
        try? data.write(to: photoURL(name))
        return name
    }

    /// Load the stored photo Data for one entry (in `photoFileNames` order).
    func photoData(for entry: FoodEntry) -> [Data] {
        entry.photoFileNames.compactMap { name in
            try? Data(contentsOf: photoURL(name))
        }
    }

    /// The file names an entry refers to (a convenience mirror of
    /// `entry.photoFileNames`; kept for call-site symmetry).
    func photoFileNames(for entryId: String) -> [String] {
        load().first(where: { $0.id == entryId })?.photoFileNames ?? []
    }

    /// Delete an entry's photo files from disk (idempotent).
    func deletePhotos(for entryId: String) {
        for name in photoFileNames(for: entryId) {
            try? FileManager.default.removeItem(at: photoURL(name))
        }
    }
}
