# 🧠 CAPE: Context-Aware Policy Engine

**Transform your Android device into an intelligent personal assistant that adapts to your life in real-time.**

CAPE is a cutting-edge Android-first automation system that leverages AI to understand your context and automatically optimize your device behavior. By sensing calendar events, location, usage patterns, and biometric signals, CAPE coordinates with OpenClaw AI agents to apply the right "behavior pack" at exactly the right time.

## 🌟 Key Features

### 📱 **Intelligent Context Sensing**
- **Calendar Intelligence**: Automatically reads upcoming meetings and prepares focus mode
- **Location Awareness**: Detects home, office, college, and commuting patterns
- **Usage Analytics**: Monitors screen time, app switching, and notification patterns
- **Sleep Tracking**: Integrates sleep debt data into decision-making
- **Commute Pressure**: Real-time traffic and travel time analysis

### 🤖 **AI-Powered Decision Making**
- **OpenClaw Integration**: Leverages advanced AI agents for intelligent decisions
- **Stress Scoring**: Real-time assessment of user cognitive load
- **Adaptive Learning**: Improves recommendations based on user feedback
- **Multi-Agent Orchestration**: Coordinates specialized AI agents for different contexts

### 🎯 **Smart Automation Packs**
- **Focus Mode**: Blocks distractions during work/study sessions
- **Relax Mode**: Optimizes device for downtime and recovery
- **Commute Mode**: Provides navigation assistance and travel updates
- **Home Mode**: Creates a comfortable personal environment

### 🔒 **Privacy-First Architecture**
- **Local Processing**: All data processed locally on your machine
- **USB Bridge**: Secure communication between phone and gateway
- **No Cloud Dependencies**: Your data never leaves your devices
- **Open Source**: Fully transparent and auditable codebase

## 🚀 Quick Start

### Prerequisites
- **Node.js 22+** (for gateway server)
- **Android SDK** with ADB (for phone communication)
- **Android device** with USB debugging enabled

### Installation & Setup

#### 🍎 macOS / Linux
```bash
# Clone and install dependencies
git clone <repository-url>
cd CAPE2
npm install

# Build the Android APK
./tools/build-apk.sh

# Start the gateway server (Terminal 1)
./tools/start-gateway.sh

# Install and launch app (Terminal 2)
./tools/install-apk.sh
```

#### 🪟 Windows
```powershell
# Clone and install dependencies
git clone <repository-url>
cd CAPE2
npm install

# Build the Android APK
.\tools\build-apk.ps1

# Start the gateway server (Terminal 1)
.\tools\start-gateway.ps1

# Install and launch app (Terminal 2)
.\tools\install-apk.ps1
```

### 🔧 Configuration

1. **Enable USB Debugging** on your Android device:
   - Settings → About Phone → Tap "Build Number" 7 times
   - Settings → Developer Options → Enable "USB Debugging"

2. **Connect your device** via USB and authorize debugging

3. **Configure Environment** (optional):
   ```bash
   cp .env.example .env
   # Edit .env with your preferences
   ```

## 📊 Architecture Overview

```
┌─────────────────┐    USB/ADB     ┌─────────────────┐    HTTP     ┌─────────────────┐
│   Android App   │ ◄────────────► │  Gateway Server │ ◄──────────► │  OpenClaw AI    │
│                 │                │   (Node.js)     │             │   Agents        │
│ • Context Sense │                │                 │             │                 │
│ • UI Interface  │                │ • Decision API  │             │ • Stress Scoring│
│ • Automation    │                │ • Feedback Loop │             │ • Learning      │
└─────────────────┘                │ • Memory Store  │             │ • Orchestration │
                                   └─────────────────┘             └─────────────────┘
```

### Core Components

- **`packages/cape-core`**: JavaScript reference implementation for stress scoring and decision logic
- **`packages/cape-gateway`**: HTTP server handling communication between Android and AI agents
- **`android/`**: Kotlin/Jetpack Compose application with modern UI
- **`openclaw/`**: AI agent configurations, memory stores, and skill definitions

## 🎮 Usage Guide

### First-Time Setup
1. **Launch the CAPE app** on your Android device
2. **Complete onboarding** with your name and frequently visited places
3. **Grant permissions** for location, calendar, notifications, and usage access
4. **Choose your current context** (Home, Office, College, Commute, or Relaxing)
5. **Apply your first behavior pack** and experience intelligent automation

### Daily Use
- **Automatic Detection**: CAPE automatically detects when you arrive at saved locations
- **Smart Transitions**: Device behavior adapts as you move between contexts
- **Feedback Loop**: Provide feedback to improve AI recommendations
- **Commute Assistance**: Get real-time traffic updates and route suggestions

## 🧪 Testing

### Core Logic Tests
```bash
npm test
```

### Gateway Tests
```bash
npm run gateway:test
```

### Demo Scenarios
```bash
npm run demo:scenarios
```

## 🔌 API Endpoints

The gateway server exposes RESTful APIs at `http://127.0.0.1:8787`:

### Core Endpoints
- `POST /v1/context/decision` - Get AI-powered context decisions
- `POST /v1/feedback` - Submit user feedback for learning
- `GET /v1/openclaw/status` - Check AI agent status

### Location Services
- `POST /v1/maps/geocode` - Convert addresses to coordinates
- `POST /v1/maps/search` - Search for places by name

## 🤖 OpenClaw Integration

CAPE leverages the OpenClaw personal AI assistant framework for advanced decision-making:

### Setup Options

#### Option 1: Full OpenClaw Integration (Recommended)
```bash
# Install OpenClaw
npm install -g openclaw@latest
openclaw onboard --install-daemon
openclaw gateway --port 18789 --verbose

# Bootstrap CAPE workspace
node tools/openclaw-bootstrap.mjs

# Start CAPE with OpenClaw brain
OPENCLAW_REQUIRED=true OPENCLAW_BASE_URL="http://127.0.0.1:18789" OPENCLAW_TOKEN="your-token" npm run gateway:start
```

#### Option 2: Local Fallback Mode
```bash
# Uses built-in decision logic when OpenClaw is unavailable
npm run gateway:start
```

### Configuration
Configure OpenClaw integration via environment variables:
- `OPENCLAW_REQUIRED=true` - Require OpenClaw for all decisions
- `OPENCLAW_BASE_URL` - OpenClaw gateway URL
- `OPENCLAW_TOKEN` - Authentication token
- `OPENCLAW_AGENT_ID` - Agent identifier (default: "cape")

## 📁 Project Structure

```
CAPE2/
├── packages/
│   ├── cape-core/          # Core decision logic and stress scoring
│   └── cape-gateway/       # HTTP gateway server
├── android/                # Android application
├── openclaw/               # AI agent configurations
├── tools/                  # Build and deployment scripts
├── docs/                   # Architecture documentation
└── .env.example            # Environment configuration template
```

## 🔧 Development

### Building from Source
```bash
# Install dependencies
pnpm install

# Build all packages
pnpm build

# Run in development mode
pnpm dev
```

### Contributing
1. Fork the repository
2. Create a feature branch
3. Implement your changes with tests
4. Submit a pull request with detailed description

## 🎯 Use Cases

### For Professionals
- **Meeting Preparation**: Automatically enables focus mode before important calls
- **Work-Life Balance**: Smoothly transitions between work and home environments
- **Productivity Optimization**: Reduces distractions during deep work sessions

### For Students
- **Study Sessions**: Creates optimal learning environment during class/homework time
- **Campus Navigation**: Assists with commute between campus locations
- **Exam Preparation**: Minimizes distractions during critical study periods

### For Everyone
- **Digital Wellness**: Promotes healthy device usage patterns
- **Sleep Quality**: Reduces evening blue light exposure
- **Stress Management**: Adapts device behavior based on cognitive load

## 🏆 Awards & Recognition

**Built for Samsung PRISM OpenClaw Hackathon** - Demonstrating the future of personal AI assistants that truly understand and adapt to human behavior.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Support

- **Documentation**: See `/docs` directory for detailed architecture notes
- **Issues**: Report bugs and request features via GitHub Issues
- **Community**: Join our Discord community for discussions and support

---

**CAPE: Where AI meets everyday life. Experience the future of personal automation today.** 🚀
