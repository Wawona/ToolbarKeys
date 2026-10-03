#if canImport(UIKit) && (os(iOS) || os(tvOS) || os(visionOS))
// Copyright (c) 2026 Rootshell LLC, Kit Knox. Toolbar starting point. MIT.
//
//  KeyboardWritingAssistanceButton.swift
//  Wawona
//
//  A native primary-action menu inside the toolbar's standard-sized key.
//  Ported 1:1 from rootshell.
//

import UIKit
import Combine

final class KeyboardWritingAssistanceButton: KeyboardSymbolButton {
    private let menuButton = UIButton(type: .system)
    private var observation: AnyCancellable?
    private static let storageKey = "wwn_terminal_writing_assistance_mode"

    public static let writingAssistanceDidChangeNotification = Notification.Name("WWNWritingAssistanceDidChangeNotification")

    init(sizes: KeyboardSizes) {
        super.init(key: "__writingAssistance__", display: .icon(TerminalWritingAssistanceMode.toolbarIcon), sizes: sizes)
        isAccessibilityElement = false
        menuButton.translatesAutoresizingMaskIntoConstraints = false
        if #available(iOS 14.0, tvOS 14.0, *) {
            menuButton.showsMenuAsPrimaryAction = true
        } else {
            menuButton.addTarget(self, action: #selector(showChoices), for: .touchUpInside)
        }
        menuButton.accessibilityLabel = NSLocalizedString("Writing Assistance", comment: "")
        addSubview(menuButton)
        NSLayoutConstraint.activate([
            menuButton.leadingAnchor.constraint(equalTo: leadingAnchor),
            menuButton.trailingAnchor.constraint(equalTo: trailingAnchor),
            menuButton.topAnchor.constraint(equalTo: topAnchor),
            menuButton.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
        let observer = NotificationCenter.default.addObserver(forName: Self.writingAssistanceDidChangeNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.refresh() }
        }
        observation = AnyCancellable { NotificationCenter.default.removeObserver(observer) }
        refresh()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    private func currentMode() -> TerminalWritingAssistanceMode {
        if let raw = UserDefaults.standard.string(forKey: Self.storageKey),
           let mode = TerminalWritingAssistanceMode(rawValue: raw) {
            return mode
        }
        return .off
    }

    @objc private func showChoices() {
        guard var presenter = window?.rootViewController else { return }
        while let presented = presenter.presentedViewController { presenter = presented }
        let alert = UIAlertController(title: menuButton.accessibilityLabel, message: nil, preferredStyle: .actionSheet)
        for choice in TerminalWritingAssistanceMode.allCases {
            alert.addAction(UIAlertAction(title: choice.title, style: .default) { _ in
                UserDefaults.standard.set(choice.rawValue, forKey: Self.storageKey)
                NotificationCenter.default.post(name: Self.writingAssistanceDidChangeNotification, object: nil)
            })
        }
        alert.addAction(UIAlertAction(title: NSLocalizedString("Cancel", comment: ""), style: .cancel))
        alert.popoverPresentationController?.sourceView = menuButton
        alert.popoverPresentationController?.sourceRect = menuButton.bounds
        presenter.present(alert, animated: true)
    }

    private func refresh() {
        let mode = currentMode()
        menuButton.accessibilityValue = mode.title
        guard #available(iOS 14.0, tvOS 14.0, *) else { return }
        menuButton.menu = UIMenu(children: TerminalWritingAssistanceMode.allCases.map { choice in
            UIAction(title: choice.title, image: UIImage(systemName: choice.icon),
                     state: choice == mode ? .on : .off) { _ in
                UserDefaults.standard.set(choice.rawValue, forKey: Self.storageKey)
                NotificationCenter.default.post(name: Self.writingAssistanceDidChangeNotification, object: nil)
            }
        })
    }
}
#endif
