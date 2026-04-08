Windows App Developer
Purpose
Provides expertise in building modern Windows desktop applications using WinUI 3, WPF, and Windows App SDK. Specializes in XAML-based UI development, MVVM architecture, native Windows integration, and modern packaging with MSIX.

When to Use
Building Windows desktop applications with WinUI 3 or WPF
Implementing MVVM architecture for Windows apps
Creating XAML layouts and custom controls
Packaging applications with MSIX
Integrating with Windows features (notifications, taskbar, system tray)
Migrating WPF applications to WinUI 3
Implementing Windows-specific features (jump lists, live tiles)
Building Microsoft Store-ready applications
Quick Start
Invoke this skill when:

Building Windows desktop applications with WinUI 3 or WPF
Implementing MVVM architecture for Windows apps
Creating XAML layouts and custom controls
Packaging applications with MSIX
Integrating with Windows features (notifications, taskbar)
Do NOT invoke when:

Building cross-platform apps → use mobile-developer or electron-pro
Console applications → use appropriate language skill
PowerShell GUI → use powershell-ui-architect
Web applications → use appropriate web skill
Decision Framework
Windows App Task?
├── New Modern App → WinUI 3 with Windows App SDK
├── Existing WPF App → Maintain or migrate to WinUI 3
├── Cross-Platform Priority → Consider .NET MAUI
├── Enterprise Internal → WPF with proven patterns
├── Store Distribution → MSIX packaging required
└── System Integration → P/Invoke or Windows SDK APIs
Core Workflows
1. WinUI 3 Application Setup
Create project using Windows App SDK template
Configure Package.appxmanifest for capabilities
Set up MVVM infrastructure (CommunityToolkit.Mvvm)
Implement navigation and shell structure
Create reusable control library
Configure MSIX packaging
Set up CI/CD for Store or sideload distribution
2. MVVM Implementation
Define ViewModels with observable properties
Implement commands for user actions
Create services for data and business logic
Set up dependency injection container
Bind Views to ViewModels in XAML
Implement navigation service
Add design-time data for XAML preview
3. MSIX Packaging
Configure Package.appxmanifest
Define application identity and capabilities
Set up visual assets (icons, splash)
Configure installation behavior
Sign package with certificate
Test installation and updates
Submit to Microsoft Store or deploy internally
Best Practices
Use WinUI 3 for new development, WPF for legacy maintenance
Implement MVVM strictly for testability and separation
Use x:Bind for compile-time binding validation
Leverage Community Toolkit for common patterns
Package with MSIX for modern installation experience
Follow Fluent Design System for consistent UX
Anti-Patterns
Code-behind logic → Move to ViewModels
Synchronous UI operations → Use async/await for I/O
Direct service calls from Views → Go through ViewModels
Ignoring DPI awareness → Test at multiple scale factors
Missing capabilities → Declare required capabilities in manifest
