import SwiftUI
import CluecoseCore

// MARK: - Liquid glass theme

enum CluecoseTheme {
    /// Deep ocean-teal backdrop so the glass panels have something to refract.
    static func backdrop() -> LinearGradient {
        LinearGradient(
            stops: [
                .init(color: Color(red: 0.02, green: 0.09, blue: 0.13), location: 0),
                .init(color: Color(red: 0.03, green: 0.16, blue: 0.19), location: 0.45),
                .init(color: Color(red: 0.05, green: 0.24, blue: 0.26), location: 0.8),
                .init(color: Color(red: 0.10, green: 0.30, blue: 0.31), location: 1),
            ],
            startPoint: .top, endPoint: .bottom
        )
    }

    static func glow(for category: RangeCategory) -> Color {
        switch category {
        case .low: return Color(red: 0.45, green: 0.78, blue: 1.0)
        case .inRange: return Color(red: 0.30, green: 0.92, blue: 0.60)
        case .high: return Color(red: 1.0, green: 0.62, blue: 0.35)
        }
    }

    static func color(for mgdl: Double) -> Color {
        glow(for: RangeCategory(mgdl: mgdl))
    }
}

struct GlassPanel: ViewModifier {
    var cornerRadius: CGFloat = 28

    func body(content: Content) -> some View {
        content
            .padding(20)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(
                        LinearGradient(colors: [.white.opacity(0.45), .white.opacity(0.06)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing),
                        lineWidth: 1
                    )
            )
            .shadow(color: .black.opacity(0.28), radius: 18, y: 10)
    }
}

extension View {
    func glassPanel(cornerRadius: CGFloat = 28) -> some View {
        modifier(GlassPanel(cornerRadius: cornerRadius))
    }
}
