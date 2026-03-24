# AnnaSuraksha - Elite Project Report

## 1. Vision
A corruption-resistant, transparent, AI-powered Public Distribution System.

## 2. Full Architecture
- API Layer (Spring Boot REST)
- Service Layer (Business Logic)
- Data Layer (JPA + MySQL)
- Fraud Engine (Rule-based + ML-ready)
- Event System (Alerts + Logs)

## 3. Detailed Workflow
1. API receives request
2. Validation layer checks input
3. Service processes transaction
4. Fraud engine computes risk
5. Decision engine flags anomalies
6. Alert service triggers notification
7. Audit service logs everything

## 4. Fraud Detection Logic
Features:
- Frequency anomalies
- Quantity mismatch
- Duplicate beneficiary usage

Risk Score = weighted sum of anomaly features

## 5. Scalability
- Microservices-ready
- Event-driven architecture
- Horizontal scaling possible

## 6. Security
- Input validation
- Audit logs
- Role-based access (future)

## 7. Future Scope
- Blockchain ledger
- Aadhaar API verification
- Real-time dashboards
