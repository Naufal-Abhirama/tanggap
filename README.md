# TANGGAP — Offline Disaster Response AI

<p align="center">
  <img src="https://img.shields.io/badge/Gemma_4-Powered-4285F4?style=for-the-badge&logo=google&logoColor=white" />
  <img src="https://img.shields.io/badge/LiteRT--LM-0.11.0--rc1-34A853?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Platform-Android_8%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/License-CC--BY_4.0-orange?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Offline-100%25-red?style=for-the-badge" />
</p>

<p align="center">
  <b>A fully offline, on-device AI disaster response assistant for Indonesia — powered by Gemma 4 via LiteRT-LM.</b><br>
  When the internet goes down during a disaster, TANGGAP stays on.
</p>

---

## 🏆 Gemma 4 Good Hackathon · Global Resilience Track

TANGGAP is our submission to the **[Gemma 4 Good Hackathon on Kaggle](https://www.kaggle.com/competitions/gemma-4-good-hackathon)**.

Indonesia ranks among the world's most disaster-prone nations — over **3,400 disaster events in 2024 alone**, affecting **8.1 million people** (BNPB, 2024). In the critical minutes after an earthquake, flood, or landslide, internet connectivity is often the first casualty. TANGGAP solves this with a fully on-device Gemma 4 AI that delivers expert disaster guidance with **zero network dependency**.

---

## 🎬 Demo

> 📹 **[Watch the demo video →](https://youtube.com/shorts/Lks618ijRsg?si=z9nm-7sV46oqf7hf)**
>
> 📓 **[Read the full Technical Proof of Work Report: Tanggap →](https://docs.google.com/document/d/1Aj8OZ9V7sHBMfaIlnD8sy5zn5V6U3EjQhk1RH1WtxaI/edit?usp=sharing)**

---

## ✨ Key Features

| Feature | Description |
|---|---|
| 🔴 **100% Offline** | Gemma 4 runs entirely on-device via LiteRT-LM. No server. No internet. |
| 🧠 **RAG + BM25** | On-device Retrieval-Augmented Generation with BM25 search over a curated BNPB knowledge base |
| 🌊 **Multi-Disaster** | Covers 9 disaster types: earthquake, tsunami, flood, landslide, volcanic eruption, wildfire, drought, extreme weather, and hazardous materials |
| 🔺 **Triage Mode** | Structured guided response for trapped victims, active disasters, and mass casualty events |
| 👁️ **Vision AI** | ML Kit image analysis to detect disaster context from camera photos |
| 🔊 **Text-to-Speech** | Spoken guidance for users with limited literacy or impaired vision |
| 📍 **Offline Location** | GPS-based detection of the nearest BPBD (regional disaster agency) contact |
| 🌐 **Bilingual** | Full support for Bahasa Indonesia and English |
| 🛡️ **Safety Layer** | Prevents hallucination by validating AI outputs against known safe content |

---

## 🏗️ Architecture

```
User Input (Text / Camera)
        │
        ▼
┌─────────────────────────┐
│   Safety Layer          │  ← Blocks unrealistic or off-scope queries
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│  Disaster Type Detector │  ← Keyword-score classifier (Gempa/Banjir/Longsor/…)
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│     RAG Pipeline        │
│  ┌───────────────────┐  │
│  │  BM25 Engine      │  │  ← Searches 500+ BNPB knowledge chunks offline
│  │  Knowledge Base   │  │  ← BNPB, BMKG, PMI, BPBD official sources
│  └───────────────────┘  │
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│   Gemma 4 (LiteRT-LM)  │  ← On-device inference, ARM64, API 26+
│   GemmaInferenceEngine  │  ← Conversation context, triage routing, prompt builder
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│  Response + TTS         │  ← Markdown-rendered UI + optional spoken output
└─────────────────────────┘
```

### Inference Routing Logic

TANGGAP intelligently routes each query through one of five response modes:

- **`CASUAL_REPLY`** — greetings, thank-you messages
- **`INFORMATIONAL`** — educational questions about disaster science
- **`EMERGENCY_GUIDANCE`** — step-by-step survival procedures
- **`ESCALATION`** — high-urgency trapped/injured scenarios with priority scoring
- **`SAFE_CONFIRMATION`** — situation de-escalation when user reports safety

---

## 📚 Knowledge Base

The knowledge base is curated from **official Indonesian government disaster documents**, processed into 500+ semantic chunks:

| Source | Coverage |
|---|---|
| BNPB — Pedoman Latihan Kesiapsiagaan 2017 | Preparedness drills, evacuation SOPs |
| BNPB — Modul KRB Tanah Longsor 2019 | Landslide risk assessment methodology |
| BNPB — Statistik Bencana Indonesia 2024 | Real disaster data and case studies |
| BMKG — Buku Saku Gempabumi & Tsunami | Earthquake/tsunami response protocols |
| BPBD DKI — Panduan Banjir Jakarta 2020 | Jakarta flood scenarios and shelter locations |
| PMI — Pedoman Pertolongan Pertama | First aid procedures (CPR, triage, fractures) |
| BNPB — Buku Saku Tanggap Tangkas | Multi-hazard emergency quick guide |

Each chunk is tagged with `disaster_type`, `topic`, `source`, and `language` for precise BM25 retrieval.

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| **On-device LLM** | Gemma 4 via `litertlm-android:0.11.0-rc1` |
| **Retrieval** | Custom BM25 engine (Kotlin, no external dependencies) |
| **Vision** | Google ML Kit Image Labeling |
| **UI** | Jetpack Compose + Material 3 |
| **TTS** | Android TextToSpeech API |
| **Location** | Android FusedLocationProvider (offline GPS) |
| **Language** | Kotlin, min API 26 (Android 8.0), ARM64 |

---

## 📊 Performance Benchmarks

All benchmarks measured on a **real Android device** running TANGGAP v1.0 with Gemma 4 via LiteRT-LM. Data captured from `GEMMA_BENCH` logcat tags during live inference sessions.

### Inference Speed by Response Mode

| Mode | Tokens Generated | Inference Time | Throughput (TPS) |
|---|---|---|---|
| `CASUAL` | 14 | 17.8 s | 0.79 |
| `EMERGENCY` | 134 | 54.1 s | 2.48 |
| `EMERGENCY` | 112 | 42.0 s | 2.66 |
| `RECOVERY` | 114 | 41.8 s | 2.73 |
| `MULTI_VICTIM` | 338 | 111.1 s | **3.04** |

> **Key insight:** Throughput *improves* at higher token counts (up to **3.04 TPS** for `MULTI_VICTIM`) as the LiteRT runtime amortizes model initialization overhead. The `CASUAL` low score (0.79 TPS / 14 tokens) reflects cold-start latency on first inference after model load. For life-critical EMERGENCY guidance, the model delivers ~130 tokens of structured survival instructions in under 55 seconds — without any network dependency.

### Memory Profile (`adb shell dumpsys meminfo id.tanggap.app`)

| Segment | PSS | RSS |
|---|---|---|
| **Native Heap** (Gemma 4 model) | 912,572 KB (~**891 MB**) | 914,084 KB |
| Java Heap (app runtime) | 16,272 KB (**~16 MB**) | 34,576 KB |
| Code | 58,760 KB | 153,364 KB |
| Graphics | 6,676 KB | 6,676 KB |
| **TOTAL** | **2,397,535 KB (~2.3 GB)** | 2,496,112 KB |

> **Key insight:** The app itself is extremely lean — only **~16 MB** Java heap. The ~891 MB native heap is the Gemma 4 model, memory-mapped directly from storage via LiteRT (`mmap`). This is the correct and expected profile for an on-device LLM. The app uses `largeHeap=true` and `noCompress` for model files to enable efficient `mmap`-based loading without decompression overhead.

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- Android device or emulator with **API 26+** and **ARM64** architecture
- ~2 GB free storage for model download

### Installation

```bash
# Clone the repository
git clone https://github.com/Naufal-Abhirama/tanggap.git
cd tanggap
```

Open in Android Studio, sync Gradle, and run on device.

### First Launch

On first launch, TANGGAP will prompt you to download the Gemma 4 model (~1.5 GB). This is a one-time download. After that, the app operates **100% offline**.

```
App Launch
    │
    ├─ Model not found → ModelDownloadScreen (one-time, Wi-Fi recommended)
    │
    └─ Model ready → LanguagePickerScreen → Main Chat UI
```

---

## 📁 Project Structure

```
app/src/main/java/id/tanggap/app/
├── cache/
│   └── EmergencyCacheManager.kt    # Caches last AI response for instant re-access
├── data/
│   ├── BM25Engine.kt               # Offline BM25 retrieval algorithm
│   ├── Chunk.kt                    # Knowledge base data model
│   ├── DisasterTypeDetector.kt     # Keyword-based disaster classifier
│   ├── KnowledgeBaseLoader.kt      # JSON knowledge base parser
│   ├── RAGPipeline.kt              # Full RAG orchestration
│   └── TriageData.kt               # Triage mode prompts (5 types × 2 languages)
├── debug/
│   └── TanggapLogger.kt            # Structured debug logging for all pipeline stages
├── download/
│   └── ModelDownloadManager.kt     # Gemma 4 model download & verification
├── inference/
│   ├── ConversationContext.kt      # Multi-turn conversation state management
│   ├── GemmaInferenceEngine.kt     # Core inference + prompt routing + safety
│   └── SafetyLayer.kt              # Output validation layer
├── location/
│   └── LocationHelper.kt           # GPS + nearest BPBD contact lookup
├── tts/
│   └── TTSManager.kt               # Text-to-speech for accessibility
├── vision/
│   ├── MLKitVisionAnalyzer.kt      # Camera image → disaster context
│   ├── VisionContext.kt            # Vision analysis data model
│   └── VisionPipeline.kt           # Full vision-to-RAG pipeline
└── ui/theme/
    ├── MainActivity.kt             # Root composable + ViewModel
    ├── ModelDownloadScreen.kt      # First-time model download UI
    ├── LanguagePickerScreen.kt     # Language selection (ID/EN)
    └── ...
```

---

## 🌍 Impact

Indonesia is the world's **4th most populous country** and faces a unique combination of geological and hydrometeorological hazards:

- **127 active volcanoes** (highest in the world)
- **5,590 river basins** with seasonal flood risk
- Located at the junction of **4 tectonic plates**
- **3,472 disaster events** recorded in 2024 (BNPB)

Existing solutions require internet connectivity — which consistently fails during the disasters that need it most. TANGGAP is designed for the reality on the ground: **no signal, no power, no time to waste**.

**Target users:** Remote communities, disaster-prone rural areas, first responders, rescue volunteers, and anyone caught in an emergency without connectivity.

---

## 🔒 Privacy & Safety

- **No data leaves the device.** Ever. All inference runs locally via LiteRT.
- **No telemetry, no analytics, no accounts required.**
- The Safety Layer filters unrealistic queries and prevents the model from fabricating emergency procedures it does not have grounded data for.
- Emergency medical procedures (CPR, first aid, self-rescue) are **always provided** — the safety layer never blocks life-saving information.

---

## 📄 License

This project is licensed under the **Creative Commons Attribution 4.0 International (CC-BY 4.0)** license.

You are free to share and adapt this work, provided appropriate credit is given.

See [`LICENSE`](./license) for full terms.

---

## 👥 Team

| Name | Role |
|---|---|
| **Naufal** | Team Lead, Product Manager, Submission |
| **Alif** | Data Engineer, Raw Document Collection & Knowledge Base Pipeline |
| **Riyu** | UI Design, Video Script |
| **Farah** | AI Mobile Engineer, Core Inference & RAG |
| **Sekar** | Researcher & Writer, Kaggle Writeup, Video Production |

---

## 🙏 Acknowledgements

- [BNPB](https://www.bnpb.go.id/) — Badan Nasional Penanggulangan Bencana
- [BMKG](https://www.bmkg.go.id/) — Badan Meteorologi, Klimatologi, dan Geofisika
- [PMI](https://www.pmi.or.id/) — Palang Merah Indonesia
- [Google DeepMind](https://deepmind.google/) — Gemma 4 model family
- [Google AI Edge](https://ai.google.dev/edge) — LiteRT-LM Android runtime
- [Kaggle](https://www.kaggle.com/) — Gemma 4 Good Hackathon platform

---

<p align="center">
  <i>Built with ❤️ for the people of Indonesia · Gemma 4 Good Hackathon 2025</i>
</p>
