# ⛓️ AnnaSuraksha PDS Ledger — Blockchain PDS Fraud Detection

> **Demo 2 of 3** | Indian Governance Hackathon | Zero JavaScript Stack

AnnaSuraksha PDS Ledger is a blockchain-based Public Distribution System (PDS) that eliminates ghost beneficiaries and enables cross-state ONORC (One Nation One Ration Card) tracking. Each ration transaction is a SHA-256 chained block. AI audits detect fraud patterns across all 36 states.

---

## Features

- **3-Layer Ghost Detection** — Duplicate Aadhaar (Layer 1) + Abnormal pattern (Layer 2) + Velocity check (Layer 3)
- **Blockchain Ledger** — SHA-256 chained blocks, each transaction links to previous
- **ONORC Tracking** — Cross-state migrant ration tracking under One Nation One Ration Card
- **AI Audit** — Groq LLM analyses entire beneficiary ledger, flags patterns, estimates financial impact
- **Category Verification** — AAY, PHH, NPHH category fraud detection
- **Zero JavaScript** — Pure server-rendered Thymeleaf HTML

## Quick Start

```bash
cd rationchain
export GROQ_API_KEY=gsk_your_key_here
mvn spring-boot:run
# Open http://localhost:8081
```

## Tech Stack

- Java 21, Spring Boot 3.2, Thymeleaf 3
- Groq API — `llama-3.3-70b-versatile`
- H2 in-memory DB (demo) | PostgreSQL-ready
- SHA-256 blockchain (Java MessageDigest)

## Project Structure

```
src/main/java/com/rationchain/
├── controller/LedgerController.java        # HTTP routes
├── model/
│   ├── Beneficiary.java                    # Beneficiary JPA entity
│   ├── RationTransaction.java              # Blockchain transaction
│   └── AuditReport.java                    # AI audit result
└── service/
    ├── AuditService.java                   # Groq AI audit
    ├── BeneficiaryService.java             # CRUD + seeding
    ├── BlockchainService.java              # SHA-256 chain
    ├── GhostDetectionService.java          # 3-layer detection
    └── ReferenceDataService.java          # States, ration quotas
```

## Configuration (application.properties)

```properties
groq.api.key=${GROQ_API_KEY:YOUR_KEY_HERE}
groq.model=llama-3.3-70b-versatile
server.port=8081
rationchain.seed.demo-data=true
```

## Ghost Detection Pipeline

### Layer 1 — Duplicate Aadhaar

Cross-references SHA-256 hashed Aadhaar numbers across all state databases. Same hash in 2+ states = ghost.

### Layer 2 — Abnormal Pattern

- AAY (Antyodaya) category assigned to single-person household → statistical anomaly
- Rice quota > 35 kg/deliveryMonth for family of 2 → over-allocation
- Ration claimed in same deliveryMonth as death certificate

### Layer 3 — Cross-State Velocity

ONORC allows migrants to collect in any state. If the same beneficiary collects in two states within 48 hours — physically impossible → fraud flag.

## AI Audit

Groq analyses the full beneficiary JSON and returns a structured report:

- Executive Summary with key numbers
- Detailed ghost list with estimated deliveryMonthly loss per entry
- Fraud pattern analysis
- Financial impact (deliveryMonthly / annual / all-India scaled to 81.35 Cr beneficiaries)
- ONORC assessment
- Policy recommendations

## vs Competitors

| Feature                         | Sangam     | DFPD Portal   | Annavitaran | Mera Ration |
| ------------------------------- | ---------- | ------------- | ----------- | ----------- |
| Blockchain immutability         | ✅         | ❌ Central DB | ❌          | ❌          |
| AI ghost detection              | ✅ 3-layer | ❌ Manual     | ❌          | ❌          |
| Real-time ONORC                 | ✅         | ❌ T+1 day    | ✅ Partial  | ✅ Partial  |
| Cross-state velocity check      | ✅         | ❌            | ❌          | ❌          |
| Audit report (financial impact) | ✅         | ❌            | ❌          | ❌          |

## Demo Data

Seeds 20 beneficiaries across 10 states including:

- 3 ghost beneficiaries (duplicate Aadhaar across states)
- 2 ONORC migrants (genuine cross-state)
- 1 category fraud (AAY mis-assigned)
- 14 verified genuine beneficiaries

---

MIT License — Indian Governance Hackathon 2025
