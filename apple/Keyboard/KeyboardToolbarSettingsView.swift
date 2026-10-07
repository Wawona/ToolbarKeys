#if canImport(UIKit) && (os(iOS) || os(tvOS) || os(visionOS))
import SwiftUI

/// Rootshell toolbar customization: reorder, hide, drawer rows, custom keys, reset.
/// Layout rules stay in `KeyboardToolbarManager`. This view only edits that store.
public struct KeyboardToolbarSettingsView: View {
    @ObservedObject private var manager = KeyboardToolbarManager.shared
    @Environment(\.presentationMode) private var presentationMode
    @State private var showingReset = false
    @State private var showingNewKey = false

    public init() {}

    public var body: some View {
        List {
            mainSection
            drawerSections
            customSection
            drawerBehaviorSection
            hiddenSection
            resetSection
        }
        .navigationBarTitle(Text("Toolbar Keys"), displayMode: .inline)
        .navigationBarItems(
            leading: Button("Done") { presentationMode.wrappedValue.dismiss() },
            trailing: EditButton()
        )
        .alert(isPresented: $showingReset) {
            Alert(
                title: Text("Reset to Defaults?"),
                message: Text("This restores the default key layout and removes custom keys."),
                primaryButton: .destructive(Text("Reset")) { manager.resetToDefaults() },
                secondaryButton: .cancel()
            )
        }
        .sheet(isPresented: $showingNewKey) {
            CustomToolbarKeySheet { label, text, sendReturn in
                var steps: [SequenceStep] = [.text(text)]
                if sendReturn {
                    steps.append(.keyCombo(SequenceStep.KeyCombo(modifiers: [], key: .special(.returnKey))))
                }
                manager.createCustomKey(CustomKey(label: label, sequence: steps))
            }
        }
    }

    private var mainSection: some View {
        Section(header: Text("Main Row")) {
            ForEach(listed(manager.config.mainRow)) { item in
                slotRow(item.slot, section: .mainRow)
            }
            .onMove { source, destination in
                var row = manager.config.mainRow
                row.move(fromOffsets: source, toOffset: destination)
                manager.setLayout(mainRow: row, drawerRows: manager.config.drawerRows)
            }
        }
    }

    private var drawerSections: some View {
        ForEach(manager.config.drawerRows.indices, id: \.self) { index in
            Section(header: Text("Drawer \(index + 1)")) {
                ForEach(listed(manager.config.drawerRows[index])) { item in
                    slotRow(item.slot, section: .drawer(index))
                }
                .onMove { source, destination in
                    var rows = manager.config.drawerRows
                    rows[index].move(fromOffsets: source, toOffset: destination)
                    manager.setLayout(mainRow: manager.config.mainRow, drawerRows: rows)
                }
            }
        }
    }

    private var customSection: some View {
        Section(
            header: Text("Custom Keys"),
            footer: Text("A custom key sends the text you enter. It is placed on the first drawer row.")
        ) {
            ForEach(manager.customKeys) { key in
                VStack(alignment: .leading, spacing: 2) {
                    Text(key.label)
                    Text(key.sequenceSummary)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            Button(action: { showingNewKey = true }) {
                Text("New Custom Key")
            }
        }
    }

    private var drawerBehaviorSection: some View {
        Section(footer: Text(drawerFooter)) {
            Stepper(value: Binding(
                get: { manager.drawerRowCount },
                set: { manager.setDrawerRowCount($0) }
            ), in: 1...KeyboardToolbarManager.maxDrawerRows) {
                HStack {
                    Text("Drawer Rows")
                    Spacer()
                    Text("\(manager.drawerRowCount)")
                        .foregroundColor(.secondary)
                }
            }
            if manager.drawerRowCount > 1 {
                Picker("More Button", selection: $manager.drawerToggleMode) {
                    ForEach(DrawerToggleMode.allCases, id: \.self) { mode in
                        Text(mode.displayName).tag(mode)
                    }
                }
            }
            Toggle("Open Drawer by Default", isOn: $manager.drawerOpenByDefault)
        }
    }

    private var hiddenSection: some View {
        Section(
            header: Text("Hidden Keys"),
            footer: Text("Tap a hidden key to put it back on the first drawer row.")
        ) {
            if manager.config.hiddenKeys.isEmpty && manager.unplacedCustomKeys.isEmpty {
                Text("No hidden keys.")
                    .foregroundColor(.secondary)
            } else {
                ForEach(manager.config.hiddenKeys.sorted { $0.rawValue < $1.rawValue }, id: \.self) { keyID in
                    Button(action: { manager.unhideKey(keyID) }) {
                        Text(keyID.displayName).foregroundColor(.primary)
                    }
                }
                ForEach(manager.unplacedCustomKeys) { key in
                    Button(action: { manager.addCustomKeyToLayout(id: key.id, section: .drawer(0)) }) {
                        Text(key.label).foregroundColor(.primary)
                    }
                }
            }
        }
    }

    private var resetSection: some View {
        Section {
            Button(action: { showingReset = true }) {
                Text("Reset to Defaults")
                    .foregroundColor(.red)
                    .frame(maxWidth: .infinity)
            }
            .disabled(!manager.isCustomized)
        }
    }

    private var drawerFooter: String {
        if manager.drawerRowCount <= 1 {
            return "When enabled, the drawer starts open each time the keyboard appears."
        }
        switch manager.drawerToggleMode {
        case .stack:
            return "Each press of the more button reveals one more drawer row, then closes them."
        case .cycle:
            return "Each press of the more button swaps the drawer to the next set of keys."
        }
    }

    private func slotRow(_ slot: KeySlot, section: KeyboardToolbarManager.ToolbarSection) -> some View {
        HStack {
            Text(title(for: slot))
            Spacer()
            if case .mainRow = section {
                Button("Drawer") {
                    manager.moveKeyToSection(slot, from: section, to: .drawer(0))
                }
                .font(.caption)
            } else {
                Button("Main") {
                    manager.moveKeyToSection(slot, from: section, to: .mainRow)
                }
                .font(.caption)
            }
            if let keyID = slot.keyID, keyID != .toolbarSettings, keyID != .drawerToggle {
                Button("Hide") { manager.hideKey(keyID) }
                    .font(.caption)
            } else if let customID = slot.customID {
                Button("Remove") { manager.removeCustomKeyFromLayout(id: customID) }
                    .font(.caption)
            }
        }
    }

    private func title(for slot: KeySlot) -> String {
        switch slot {
        case .builtIn(let keyID):
            return keyID.displayName
        case .custom(let id):
            return manager.customKey(for: id)?.label ?? "Custom"
        }
    }

    private func listed(_ slots: [KeySlot]) -> [ListedSlot] {
        slots.map { ListedSlot(slot: $0) }
    }
}

private struct ListedSlot: Identifiable {
    let slot: KeySlot
    var id: String {
        switch slot {
        case .builtIn(let key):
            return "b-" + key.rawValue
        case .custom(let uuid):
            return "c-" + uuid.uuidString
        }
    }
}

private struct CustomToolbarKeySheet: View {
    @Environment(\.presentationMode) private var presentationMode
    @State private var label = ""
    @State private var text = ""
    @State private var sendReturn = true
    let onSave: (String, String, Bool) -> Void

    var body: some View {
        NavigationView {
            Form {
                TextField("Label", text: $label)
                TextField("Text to send", text: $text)
                Toggle("Then press Return", isOn: $sendReturn)
            }
            .navigationBarTitle(Text("New Custom Key"), displayMode: .inline)
            .navigationBarItems(
                leading: Button("Cancel") { presentationMode.wrappedValue.dismiss() },
                trailing: Button("Save") {
                    let trimmed = label.trimmingCharacters(in: .whitespaces)
                    guard !trimmed.isEmpty, !text.isEmpty else { return }
                    onSave(trimmed, text, sendReturn)
                    presentationMode.wrappedValue.dismiss()
                }
                .disabled(label.trimmingCharacters(in: .whitespaces).isEmpty || text.isEmpty)
            )
        }
    }
}
#endif
