<div align="center">

<!-- University crest goes here if you have one -->

# A Full-Stack Framework for Secure Multi-Tenant Customer Relationship Management

*A research project submitted in partial fulfillment of the requirements for the degree of*
*Bachelor of Science (Engineering) in Computer Science and Engineering*

<br>

**Submitted By**

**[Your Name]**
[Your Student ID]

**[Co-author Name]**
[Co-author Student ID]

<br>

**Supervised By**

**[Supervisor Name]**
[Supervisor Designation]

<br>

**Department of Computer Science and Engineering**
**Mawlana Bhashani Science and Technology University**
**Santosh, Tangail-1902, Bangladesh**

</div>

<br>

> **Note for the authors:** Replace the bracketed `[…]` placeholders above (names, IDs, supervisor,
> and — if different — the institution) before submission. Diagrams in Chapter 3 use
> [Mermaid](https://mermaid.js.org/), which renders natively on GitHub and in most Markdown viewers;
> the screenshots referenced in Chapter 6 live in [`screenshots/`](./screenshots/).

---

## Approval

The Research Project Report about developing **"A Full-Stack Framework for Secure Multi-Tenant
Customer Relationship Management"** submitted by **[Your Name] (ID: [Your ID])** and
**[Co-author Name] (ID: [Co-author ID])** to the Department of Computer Science and Engineering,
Mawlana Bhashani Science and Technology University, Santosh, Tangail-1902, Bangladesh, has been
accepted as satisfactory for the partial fulfillment of the requirements for the degree of Bachelor
of Science (Engineering) in Computer Science and Engineering and approved as to its style and contents.

<br>

**Board of examiners:**

1. ............................................................ (Supervisor)

2. ............................................................ (Examiner)

3. ............................................................ (Examiner)

---

## Declaration

Hereby, we declare that the project development work, overseen by **[Supervisor Name]**,
[Supervisor Designation], Department of Computer Science and Engineering, Mawlana Bhashani Science
and Technology University, Santosh, Tangail-1902, Bangladesh, has been completed. Furthermore, we
affirm that no portion of this project has been or will be submitted for consideration for another
degree elsewhere.

<br>

| Countersigned | Signature |
|---|---|
| | |
| ..................................... | ..................................... |
| **[Supervisor Name]** | **[Your Name] ([Your ID])** |
| [Supervisor Designation] | |
| Dept. of CSE | ..................................... |
| Supervisor | **[Co-author Name] ([Co-author ID])** |
| | Candidate |

---

## Abstract

Modern organizations run their sales and customer relationships on software, but most off-the-shelf
Customer Relationship Management (CRM) systems are either too expensive, too rigid, or — critically —
not built to safely host many independent organizations on the same deployment. *Synoptic Engine* is a
full-stack, **multi-tenant** CRM framework that addresses these problems. A single running instance can
serve many separate companies ("tenants") at once, with each tenant's data strictly isolated from every
other. The system covers the full sales lifecycle: lead management with configurable pipelines, contact
management for persons and organizations, activity tracking, product and quote generation, and an
inventory module for warehouses, stock, and stock movements. It is built on a Kotlin and Spring
Modulith backend with a PostgreSQL database, and a Nuxt 4 (Vue 3) single-page web client. The defining
engineering challenge of a multi-tenant CRM is **tenant isolation** — ensuring one company can never see
or modify another's data — and this project tackles it with a defense-in-depth strategy that combines
PostgreSQL Row-Level Security with application-layer Hibernate filters, layered on top of JSON Web Token
authentication, role-based access control, and optional multi-factor authentication. The result is a
working, end-to-end CRM that demonstrates how a secure multi-tenant platform can be designed,
implemented, and validated, and serves as a model for building software-as-a-service business
applications in similar contexts.

---

## Acknowledgement

Above all, we are thankful to God for providing us with the wisdom, drive, and endurance necessary to
successfully finish the project. We would like to extend our sincerest thanks to our project
supervisor, **[Supervisor Name]**, [Supervisor Designation] in the Department of Computer Science and
Engineering at Mawlana Bhashani Science and Technology University. His guidance, expertise, and
continuous support have been instrumental in the success of this project.

We also want to express our gratitude to our team members for their commitment, diligence, and
cooperation during the whole development process. We express our gratitude to all of the volunteers for
their time and insightful comments throughout the testing and assessment process. Their advice and
insights have been very helpful in improving and honing the application. Finally, we would like to
thank our loved ones, families, and friends for their steadfast encouragement and support. Throughout
this journey, their faith in us has served as a constant source of inspiration. We sincerely appreciate
all of your wonderful help and contributions.

---

## Contents

- [Chapter 1 — Introduction](#chapter-1--introduction)
  - [1.1 Project Definition](#11-project-definition)
  - [1.2 Motivation](#12-motivation)
  - [1.3 Objective](#13-objective)
  - [1.4 Related Work](#14-related-work)
- [Chapter 2 — Methodology and Requirement Analysis](#chapter-2--methodology-and-requirement-analysis)
  - [2.1 Project Definition](#21-project-definition)
  - [2.2 Project Purpose](#22-project-purpose)
  - [2.3 Project Scope](#23-project-scope)
  - [2.4 Requirements](#24-requirements)
  - [2.5 System Analysis](#25-system-analysis)
  - [2.6 Analysis Model](#26-analysis-model)
  - [2.7 Graphical User Interface](#27-graphical-user-interface)
  - [2.8 Number of Modules](#28-number-of-modules)
    - [2.8.1 User Modules](#281-user-modules)
    - [2.8.2 Admin Modules](#282-admin-modules)
  - [2.9 System Planning](#29-system-planning)
  - [2.10 Feasibility Study](#210-feasibility-study)
  - [2.11 Technology and System Feasibility](#211-technology-and-system-feasibility)
  - [2.12 Operational Feasibility](#212-operational-feasibility)
  - [2.13 Economic Feasibility](#213-economic-feasibility)
  - [2.14 Technical Feasibility](#214-technical-feasibility)
- [Chapter 3 — Project Design](#chapter-3--project-design)
  - [3.1 Design Specification](#31-design-specification)
  - [3.2 Use Case Diagram](#32-use-case-diagram)
  - [3.3 Class Diagram](#33-class-diagram)
  - [3.4 Data Flow Diagram](#34-data-flow-diagram)
  - [3.5 ER Diagram](#35-er-diagram)
- [Chapter 4 — Technical Tools](#chapter-4--technical-tools)
  - [4.1 Visual Studio Code](#41-visual-studio-code)
  - [4.2 Kotlin and Spring Boot](#42-kotlin-and-spring-boot)
  - [4.3 Spring Modulith](#43-spring-modulith)
  - [4.4 Nuxt and Vue](#44-nuxt-and-vue)
  - [4.5 PostgreSQL](#45-postgresql)
  - [4.6 Languages](#46-languages)
    - [4.6.1 Kotlin](#461-kotlin)
    - [4.6.2 TypeScript](#462-typescript)
    - [4.6.3 SQL](#463-sql)
- [Chapter 5 — Testing, Security and Maintenance](#chapter-5--testing-security-and-maintenance)
  - [5.1 Methodology used for testing](#51-methodology-used-for-testing)
  - [5.2 Testing Methods](#52-testing-methods)
  - [5.3 Black Box Testing](#53-black-box-testing)
  - [5.4 White Box Testing](#54-white-box-testing)
  - [5.5 Security](#55-security)
  - [5.6 Maintenance](#56-maintenance)
- [Chapter 6 — Project Features and Functionalities](#chapter-6--project-features-and-functionalities)
  - [6.1 Dashboard](#61-dashboard)
  - [6.2 Authentication and Access Control](#62-authentication-and-access-control)
  - [6.3 Leads and Pipelines](#63-leads-and-pipelines)
  - [6.4 Contacts](#64-contacts)
  - [6.5 Products and Quotes](#65-products-and-quotes)
  - [6.6 Inventory](#66-inventory)
  - [6.7 Activities](#67-activities)
  - [6.8 Mail](#68-mail)
  - [6.9 Cross-Tenant Sharing](#69-cross-tenant-sharing)
  - [6.10 Settings and Administration](#610-settings-and-administration)
  - [6.11 Automation, Web-Forms and Webhooks](#611-automation-web-forms-and-webhooks)
- [Chapter 7 — Limitations, Future Work and Conclusion](#chapter-7--limitations-future-work-and-conclusion)
  - [7.1 Limitations](#71-limitations)
  - [7.2 Future Work](#72-future-work)
  - [7.3 Conclusion](#73-conclusion)
- [References](#references)

---

## List of Figures

| Figure | Title |
|---|---|
| Figure 1 | Use Case Diagram |
| Figure 2 | Class Diagram |
| Figure 3 | Data Flow Diagram |
| Figure 4 | ER Diagram |
| Figure 5 | Dashboard (Home) |
| Figure 6 | Leads List |
| Figure 7 | Leads Pipeline (Kanban) |
| Figure 8 | Lead Detail and Timeline |
| Figure 9 | Persons (Contacts) |
| Figure 10 | Organizations |
| Figure 11 | Products |
| Figure 12 | Quote Detail |
| Figure 13 | Activities |
| Figure 14 | Warehouses |
| Figure 15 | Inventory — Stock |
| Figure 16 | Inventory — Reorder |
| Figure 17 | Inventory — Transfers |
| Figure 18 | Mail (Sent) |
| Figure 19 | Sharing — Relationships |
| Figure 20 | Sharing — Shared-with-me |
| Figure 21 | Cross-Tenant Audit |
| Figure 22 | Settings — Pipelines |
| Figure 23 | Settings — Users |
| Figure 24 | Settings — Roles |
| Figure 25 | Settings — Attributes (EAV) |
| Figure 26 | Settings — Tenants |

## List of Tables

| Table | Title |
|---|---|
| Table 1 | System Comparison |
| Table 2 | Related Work |
| Table 3 | Requirements |
| Table 4 | Software Maintenance |

---

# Chapter 1 — Introduction

## 1.1 Project Definition

The project aims to develop and implement a full-stack web application designed to deliver a secure,
multi-tenant Customer Relationship Management (CRM) platform. A single deployment of the application
can host many independent organizations — called *tenants* — at the same time, while keeping each
tenant's data completely isolated from every other. By leveraging modern web and database technologies,
the application seeks to address the key challenges faced by sales teams, managers, and administrators:
tracking leads through a sales pipeline, managing contacts and organizations, producing quotes, and
controlling inventory — all without sacrificing the data privacy and access control that a shared,
multi-customer platform demands.

## 1.2 Motivation

Almost every business that sells a product or a service depends on a CRM to manage its prospects,
customers, and deals. Yet good CRM software is often expensive, heavy, and difficult to customize, and
the architecture that makes a CRM commercially viable — serving many paying companies from one
installation — is also the source of its hardest engineering problem: **tenant isolation**. If the
isolation boundary leaks, one customer can read or modify another customer's confidential sales data,
which is catastrophic for trust and for the law. Many tutorials and small projects build a CRM for a
*single* organization and quietly ignore this problem. Our motivation is to confront it directly: to
build a CRM that is genuinely multi-tenant and to make the isolation boundary the central design
concern, defended in depth rather than assumed. In doing so we also wanted to exercise a realistic,
production-grade technology stack — a modular Kotlin/Spring backend and a modern Nuxt frontend — so that
the result is not a toy, but a credible model of how software-as-a-service business applications are
actually built. Let us look at the comparison between a typical single-tenant approach and our proposed
system:

| Typical Single-Tenant CRM | Proposed System (Synoptic Engine) |
|---|---|
| One database/instance per customer | One deployment serves many tenants |
| Isolation handled by separate deployments | Isolation enforced inside one shared database |
| Hard to scale to many small customers | Economical at any number of tenants |
| Tenant boundary is an operational concern | Tenant boundary is enforced in code + database |
| No cross-tenant collaboration | Controlled, audited cross-tenant data sharing |

**Table 1: System Comparison**

## 1.3 Objective

The objective of this project is to create a secure, user-friendly CRM platform for organizations and
their sales teams. The platform aims to achieve the following objectives:

- ❖ **Multi-tenant isolation:** Allow many independent organizations to use one deployment, guaranteeing
  that no tenant can ever read or write another tenant's data.
- ❖ **End-to-end sales workflow:** Provide leads, configurable pipelines, contacts (persons and
  organizations), activities, products, and quotes in one coherent system.
- ❖ **Inventory management:** Track products across multiple warehouses, including stock levels, stock
  movements, transfers, and low-stock reorder alerts.
- ❖ **Secure authentication:** Implement JSON Web Token authentication, role-based access control, and
  optional multi-factor authentication to protect every account.
- ❖ **Controlled collaboration:** Let two tenants form an explicit relationship and share selected
  records with each other, with a full cross-tenant audit trail.
- ❖ **Extensibility:** Allow administrators to customize pipelines, custom attributes, email templates,
  automation workflows, and public lead-capture web-forms without changing code.
- ❖ **Maintainability:** Build on a modular architecture with automated tests so the system can grow
  and be maintained safely.

## 1.4 Related Work

There are many CRM products on the market. Below we compare two widely used systems with our proposed
system across the dimensions that matter for this project.

| Features | Salesforce [1] | Krayin CRM [2] | Proposed System |
|---|---|---|---|
| Multi-tenant SaaS | Yes (proprietary) | No (single-tenant per install) | Yes (open architecture) |
| Self-hostable | No | Yes | Yes |
| Cost | High subscription | Free / open-source | Free (self-hosted) |
| Configurable pipelines & attributes | Yes | Yes | Yes |
| Built-in cross-tenant sharing | N/A | No | Yes |
| Database-level tenant isolation (RLS) | Internal | No | Yes |

**Table 2: Related Work**

Salesforce is the dominant commercial CRM and is multi-tenant, but it is proprietary, expensive, and
cannot be self-hosted or studied. Krayin is a popular open-source CRM whose feature set inspired much of
our domain model, but it is designed to run one organization per installation and does not address
multi-tenant isolation. Our proposed system occupies the gap between them: an open, self-hostable CRM
that is multi-tenant by design and that treats isolation as a first-class, defended boundary.

---

# Chapter 2 — Methodology and Requirement Analysis

## 2.1 Project Definition

The project aims to develop and implement a full-stack web application that delivers a secure
multi-tenant CRM. By leveraging a modular Kotlin/Spring backend and a Nuxt frontend, the application
seeks to address the key challenges faced by sales teams and administrators — managing leads, contacts,
quotes, and inventory — while guaranteeing that each tenant's data remains fully isolated within a single
shared deployment [3].

## 2.2 Project Purpose

This research project aims to develop a software platform that improves how organizations manage their
customer relationships, while solving the multi-tenancy problem that makes such platforms economical to
operate. The web application automates everyday sales tasks such as capturing leads, moving them through
a pipeline, recording activities, and generating quotes. This frees sales teams from manual spreadsheets
and lets them focus on selling. The application also provides inventory features — products, warehouses,
stock, and movements — so that a sales quote can be tied to real, tracked goods. Because it is a web
application, it is accessible from any device with an internet connection and a browser, improving access
to information for everyone in the organization, regardless of location. Crucially, the platform is built
so that many organizations can use the same running instance safely, with strict data isolation, which is
the property that distinguishes a commercial-grade CRM from a single-company tool.

## 2.3 Project Scope

The project scope outlines the specific features, functionalities, and deliverables included in the
development of the multi-tenant CRM. The overview of the project scope is given below:

- ❖ **Tenancy & Login:** Each user belongs to a tenant. Users authenticate with email and password (and
  optionally a multi-factor code) to receive a JSON Web Token that scopes every request to their tenant.
- ❖ **Leads:** Users can create leads, attach them to persons/organizations and products, and move them
  through configurable pipeline stages (e.g. New → Qualified → Proposal → Won/Lost).
- ❖ **Contacts:** Users can manage *persons* and *organizations*, link them together, tag them, and
  attach custom attributes.
- ❖ **Products & Quotes:** Admins maintain a product catalogue with SKUs and prices; users build
  multi-line quotes with automatically computed subtotal, tax, and total.
- ❖ **Inventory:** Users can view stock per warehouse, record stock movements (reserve/release), perform
  inter-warehouse transfers, and see low-stock reorder alerts.
- ❖ **Activities:** Users can log calls, meetings, tasks, and notes against leads and contacts, forming a
  timeline.
- ❖ **Mail:** Users can compose and send email (sent items and drafts), backed by email templates.
- ❖ **Sharing:** Admins of two tenants can form a relationship and share selected records both ways, with
  every cross-tenant access recorded in an audit log.
- ❖ **Settings:** Admins can configure pipelines, sources, types, tags, custom attributes, email
  templates, automation workflows, public web-forms, webhooks, CSV imports, users, roles, and groups.
- ❖ **Dashboard:** Users can view aggregate statistics — lead counts, pipeline funnel, won revenue, top
  products and customers, and recent/upcoming activities.

## 2.4 Requirements

For building this platform some requirements are needed. We provide these requirements in three parts.
These are:

- ❖ Project Requirements
- ❖ Software Requirements
- ❖ Hardware Requirements

| Project Requirements | Software Requirements | Hardware Requirements |
|---|---|---|
| Complete design diagrams (use case, class, DFD, ER) | Backend: Kotlin 2.x on JDK 25, Spring Boot 4 + Spring Modulith | CPU: dual-core or better |
| Complete source code for frontend and backend | Database: PostgreSQL 16 | RAM: 4 GB or upper (8 GB recommended) |
| Database migration scripts (Flyway) | Frontend: Nuxt 4 (Vue 3) with Nuxt UI 4 + Tailwind CSS 4 | HDD/SSD: enough to store the database and build artifacts |
| Automated test suite | Tooling: Gradle, pnpm, Docker (Postgres + Mailpit) | Network: internet access for clients and email |

**Table 3: Requirements**

## 2.5 System Analysis

This section describes the system's overall structure as well as its features. Synoptic Engine follows a
classic client–server, three-layer architecture. The **client** is a Nuxt 4 single-page application that
runs in the browser and renders all screens and forms. The **server** is a Kotlin/Spring Modulith
application that exposes a REST API, enforces authentication and tenant isolation, executes business
logic, and persists data. The **data layer** is a PostgreSQL database accessed through Hibernate/JPA,
with schema evolution managed by Flyway migrations. Analysis and design are crucial for overseeing the
entire development cycle, because any architectural flaw — especially in the tenant-isolation
boundary — is expensive to fix later. One essential element of the system that users interact with is its
user interface, which includes forms and screens for data entry, list/detail views for navigation, and
the dashboard the system produces. A defining design decision in the analysis phase was that the tenant
boundary must be enforced at *two* independent layers — the application (Hibernate filter) and the
database (Row-Level Security) — so that a mistake in one layer does not become a data breach.

## 2.6 Analysis Model

This document is important to the software development life cycle (SDLC) as it records all of the
application's requirements [4]. For this project, the **Agile model** was adopted. The Agile model is an
iterative software-development approach in which work is divided into smaller increments, or *iterations*,
that do not require long-term up-front planning. Each iteration is a short "frame," typically lasting one
to four weeks, that produces a working slice of the system. By breaking the project into smaller parts,
project risk is reduced and the overall delivery time is shortened.

The basic steps of the Agile model, as applied to this project, are:

- ❖ The requirements for the platform are gathered and prioritized (multi-tenancy first, then core CRM,
  then inventory, then settings/automation).
- ❖ The features included in each iteration are planned in a delivery plan.
- ❖ Building of the software is done using frequent, rapid iterations, each ending in a runnable system.
- ❖ The software is put through a rigorous testing process (unit, integration with Testcontainers, and
  end-to-end browser tests) to make sure it is high-quality and fits the needs of the user.
- ❖ The program is deployed and utilized.
- ❖ The software is maintained to make sure it keeps up with the demands and expectations of the users.

## 2.7 Graphical User Interface

To maximize its versatility, the interface was designed with a graphical concept in mind and delivered
through a web browser. A graphical user interface (GUI) is a software interface that leverages the
computer's visual capabilities to enhance user experience [5]. The Synoptic Engine web client is built
with Nuxt UI 4 — a component library on top of Tailwind CSS — which gives the application a unified and
professional appearance through consistent typography, spacing, colour, and iconography. The interface
uses clear navigation with descriptive labels and icons so that users can quickly locate leads,
contacts, products, inventory, and settings. List views, detail pages, modal forms, and a Kanban-style
pipeline board are all presented in a consistent visual style, and the layout is responsive so it remains
usable across screen sizes.

## 2.8 Number of Modules

The following modules are present in the application. We have a user module and an admin module. Every
action in both modules is automatically scoped to the signed-in user's tenant.

### 2.8.1 User Modules

- Login (with optional multi-factor authentication)
- Manage Leads and move them through the pipeline
- Manage Contacts (persons and organizations)
- Log Activities (calls, meetings, tasks, notes)
- Build and view Quotes
- View Inventory (stock, movements, transfers, reorder alerts)
- Compose and view Mail
- View the Dashboard, statistics, and shared records

### 2.8.2 Admin Modules

- Admin has overall control over the tenant's configuration
- Manage Users, Roles, and Groups (role-based access control)
- Configure Pipelines, Sources, Types, Tags, and custom Attributes
- Manage Products, Warehouses, and Email Templates
- Define Automation Workflows, public Web-Forms, and Webhooks
- Run CSV Imports
- Form cross-tenant Sharing relationships and review the Cross-Tenant Audit
- (Platform owner) Manage Tenants

## 2.9 System Planning

Project planning is the process of applying information, skills, instruments, and methods to project
needs. The planning stage follows initiation and produces the strategy for execution. For this project,
planning began by defining the objectives, analyzing the requirements, and prioritizing features by
their importance and risk. Because the multi-tenant isolation boundary is the highest-risk part of the
system, it was scheduled *first* — the data model, the tenant-context propagation, the Hibernate filter,
and the Row-Level Security baseline were built and tested before any user-facing CRM feature. Core CRM
features (leads, contacts, quotes) followed, then inventory, then settings, automation, and cross-tenant
sharing. Managing scope and identifying and mitigating risks — chiefly the risk of a cross-tenant data
leak — were essential to ensure a solid foundation for successful execution.

## 2.10 Feasibility Study

A feasibility study employs comprehensive investigation and research to assess the viability of a
project, furnishing decision-makers with the data they need. It seeks to rationally and impartially
determine the benefits, drawbacks, and risks of a proposed endeavour, the resources required, and,
ultimately, the probability of success. In short, the two elements that determine feasibility are the
value to be realized and the cost necessary. For this project, the value is a working, secure
multi-tenant CRM and the engineering knowledge of how to build one; the cost is open-source tooling and
development effort. The study below considers technical, operational, economic, and technical-resource
feasibility.

## 2.11 Technology and System Feasibility

To identify whether the team had the technical know-how to complete the project, the evaluation was
based on an outline of the system requirements. The project uses mature, well-documented, open-source
technologies: Kotlin and Spring Boot for the backend, PostgreSQL for storage, and Nuxt/Vue for the
frontend. All of these are freely available and widely supported, and PostgreSQL provides Row-Level
Security natively, which is essential to our isolation design. At this point the question is whether the
plan is feasible in terms of technology and law — and it is, because every required capability (RLS,
JWT, role-based access control) is provided by the chosen platform.

## 2.12 Operational Feasibility

Operational feasibility is the degree to which a proposed system meets the requirements found during
analysis, solves the identified problems, and is usable in practice. The system is operationally
feasible because it directly addresses the day-to-day needs of a sales team — capturing and progressing
leads, managing contacts, quoting, and tracking inventory — through a clean web interface that requires
no installation on the client beyond a browser. Administrators can configure the system to their own
process (pipelines, attributes, roles) without developer involvement, which means the workforce can
adopt it without disruptive change.

## 2.13 Economic Feasibility

The project's benefits and costs are examined in the economic feasibility study. The development cost is
dominated by engineering time, because the entire technology stack is free and open-source: there are no
licensing fees for Kotlin, Spring, PostgreSQL, Nuxt, or the development tooling. Operationally, the
multi-tenant design is the key economic advantage — one deployment can serve many organizations, so the
marginal cost of onboarding an additional tenant is negligible compared with running a separate instance
per customer. A cost–benefit analysis therefore strongly favours the project.

## 2.14 Technical Feasibility

Technical feasibility involves analyzing the available hardware, software, and technology resources
needed to build the project. The required technologies — a JVM runtime, PostgreSQL, Node.js for the
frontend toolchain, and Docker for local infrastructure — are all readily available and run on modest
hardware. Maintenance and upgrades are straightforward because the architecture is modular (Spring
Modulith enforces module boundaries) and the database schema is versioned with Flyway migrations. The
team's familiarity with the languages and frameworks, combined with the strong documentation and
community support behind each tool, confirms the technical feasibility of the project.

---

# Chapter 3 — Project Design

## 3.1 Design Specification

A design specification is a blueprint that describes how the software should be built and how its parts
fit together. It helps developers create software that is well-structured, easy to understand and
maintain, and that meets the requirements of the stakeholders. Synoptic Engine is designed with an
object-oriented, domain-driven approach on the backend, organized into Spring Modulith modules
(`identity`, `auth`, `crm`, `inventory`, `sharing`, `settings`, `dashboard`, `shared`), each owning its
own entities, services, and REST controllers. The following diagrams describe the system's behaviour
(use case), structure (class), data movement (data flow), and persistent data model (ER).

## 3.2 Use Case Diagram

A use case diagram represents the graphical interactions between the users and the system. It is used in
system analysis to identify, clarify, and organize the system requirements. The primary goal is to give
a high-level overview of the functionality of the system and how it interacts with outside entities. In
Synoptic Engine the two principal actors are the **Admin** (a tenant administrator) and the **User** (a
regular tenant member); both are always scoped to their own tenant.

```mermaid
graph LR
    Admin([Admin]):::actor
    User([User]):::actor

    subgraph System[Synoptic Engine]
        L((Login / MFA))
        VD((View Dashboard))
        ML((Manage Leads))
        VP((Move Pipeline Stage))
        MC((Manage Contacts))
        LA((Log Activities))
        MQ((Manage Quotes))
        VI((View Inventory))
        SM((Send Mail))
        CFG((Configure Settings))
        UR((Manage Users & Roles))
        SH((Manage Sharing))
        AUD((View Cross-Tenant Audit))
    end

    User --- L
    User --- VD
    User --- ML
    User --- VP
    User --- MC
    User --- LA
    User --- MQ
    User --- VI
    User --- SM

    Admin --- L
    Admin --- CFG
    Admin --- UR
    Admin --- SH
    Admin --- AUD
    Admin --- ML
    Admin --- MQ

    classDef actor fill:#fff,stroke:#333,stroke-width:1px;
```

**Figure 1: Use Case Diagram**

## 3.3 Class Diagram

A class diagram describes the structure of the system by showing its classes, their attributes, and the
relationships among them. The diagram below shows the core domain classes of Synoptic Engine. Note that
every tenant-owned entity carries a `tenantId`, which is the field on which all isolation is enforced.

```mermaid
classDiagram
    class Tenant {
        +UUID id
        +String name
        +String status
    }
    class User {
        +UUID id
        +UUID tenantId
        +String email
        +String passwordHash
        +boolean mfaEnabled
    }
    class Role {
        +UUID id
        +UUID tenantId
        +String name
        +Set~Permission~ permissions
    }
    class Lead {
        +UUID id
        +UUID tenantId
        +String title
        +Money value
        +PipelineStage stage
    }
    class Pipeline {
        +UUID id
        +UUID tenantId
        +String name
        +List~Stage~ stages
    }
    class Person {
        +UUID id
        +UUID tenantId
        +String name
        +String email
    }
    class Organization {
        +UUID id
        +UUID tenantId
        +String name
    }
    class Activity {
        +UUID id
        +UUID tenantId
        +String type
        +Instant dueAt
    }
    class Product {
        +UUID id
        +UUID tenantId
        +String sku
        +Money price
    }
    class Quote {
        +UUID id
        +UUID tenantId
        +Money subtotal
        +Money tax
        +Money total
    }
    class Warehouse {
        +UUID id
        +UUID tenantId
        +String name
    }
    class Inventory {
        +UUID id
        +UUID tenantId
        +int quantity
        +int reserved
    }
    class TenantRelationship {
        +UUID id
        +UUID tenantA
        +UUID tenantB
        +String status
    }

    Tenant "1" --> "*" User
    Tenant "1" --> "*" Role
    User "*" --> "*" Role
    Tenant "1" --> "*" Pipeline
    Pipeline "1" --> "*" Lead
    Tenant "1" --> "*" Lead
    Lead "*" --> "0..1" Person
    Lead "*" --> "0..1" Organization
    Person "*" --> "0..1" Organization
    Lead "1" --> "*" Activity
    Lead "*" --> "*" Product
    Quote "*" --> "1" Lead
    Quote "1" --> "*" Product : line items
    Warehouse "1" --> "*" Inventory
    Product "1" --> "*" Inventory
    Tenant "1" --> "*" TenantRelationship
```

**Figure 2: Class Diagram**

## 3.4 Data Flow Diagram

A data flow diagram (DFD) shows how data moves through the system — from the user, through processing,
to storage, and back. The diagram below shows a representative flow: a request from the browser is
authenticated, stamped with the tenant context, processed by a service, and persisted in PostgreSQL,
where both the Hibernate filter and Row-Level Security constrain it to the correct tenant.

```mermaid
flowchart TD
    U[User Browser - Nuxt SPA] -->|HTTPS request + JWT| API[Spring REST Controller]
    API -->|validate token| AUTH[Auth & JWT Filter]
    AUTH -->|set tenant context| TC[TenantContext]
    TC --> SVC[Domain Service - business logic]
    SVC -->|JPA query with @Filter| HIB[Hibernate / JPA]
    HIB -->|SQL constrained by RLS| DB[(PostgreSQL)]
    DB -->|tenant-scoped rows| HIB
    HIB --> SVC
    SVC -->|DTO / ProblemDetail| API
    API -->|JSON response| U
    SVC -.->|domain events| ASYNC[Async: email, webhooks, audit log]
```

**Figure 3: Data Flow Diagram**

## 3.5 ER Diagram

An Entity-Relationship (ER) diagram is a graphical representation that illustrates the relationships
between entities in a database system. It is a blueprint that visually shows how the different parts of
the data are connected. The simplified ER diagram below shows the principal tables of Synoptic Engine and
the foreign-key relationships between them; the `tenant_id` column appears on every tenant-owned table
and is the column that Row-Level Security policies key on.

```mermaid
erDiagram
    TENANT ||--o{ APP_USER : has
    TENANT ||--o{ ROLE : has
    APP_USER }o--o{ ROLE : assigned
    TENANT ||--o{ PIPELINE : has
    PIPELINE ||--o{ PIPELINE_STAGE : contains
    PIPELINE ||--o{ LEAD : organizes
    TENANT ||--o{ LEAD : owns
    PERSON ||--o{ LEAD : "linked to"
    ORGANIZATION ||--o{ LEAD : "linked to"
    ORGANIZATION ||--o{ PERSON : employs
    LEAD ||--o{ ACTIVITY : has
    LEAD ||--o{ QUOTE : generates
    QUOTE ||--o{ QUOTE_LINE : contains
    PRODUCT ||--o{ QUOTE_LINE : "appears in"
    WAREHOUSE ||--o{ INVENTORY : stocks
    PRODUCT ||--o{ INVENTORY : "stored as"
    INVENTORY ||--o{ STOCK_MOVEMENT : records
    TENANT ||--o{ TENANT_RELATIONSHIP : "party to"
    TENANT_RELATIONSHIP ||--o{ SHARED_RECORD : shares
    TENANT ||--o{ AUDIT_LOG : writes

    TENANT {
        uuid id PK
        string name
        string status
    }
    APP_USER {
        uuid id PK
        uuid tenant_id FK
        string email
        string password_hash
        bool mfa_enabled
    }
    LEAD {
        uuid id PK
        uuid tenant_id FK
        string title
        numeric value
        uuid stage_id FK
    }
    PRODUCT {
        uuid id PK
        uuid tenant_id FK
        string sku
        numeric price
    }
    INVENTORY {
        uuid id PK
        uuid tenant_id FK
        uuid warehouse_id FK
        uuid product_id FK
        int quantity
        int reserved
    }
```

**Figure 4: ER Diagram**

---

# Chapter 4 — Technical Tools

## 4.1 Visual Studio Code

Visual Studio Code, often referred to as VS Code, is a popular free and open-source source-code editor.
Developed by Microsoft and available for Windows, macOS, and Linux, its key features include support for
many languages, intelligent code completion, integrated debugging, and built-in Git integration. The
project was developed in VS Code (running inside a reproducible Dev Container), which made it a
convenient single environment for editing the Kotlin backend, the Vue/TypeScript frontend, and the SQL
migrations together.

## 4.2 Kotlin and Spring Boot

Kotlin is a modern, statically-typed programming language that runs on the Java Virtual Machine. It is
concise, null-safe, and fully interoperable with the Java ecosystem, which makes it an excellent choice
for backend development. Spring Boot is the de-facto framework for building production Java/Kotlin web
services; it provides auto-configuration, an embedded web server, dependency injection, and starters for
data access, security, validation, and email [6]. Synoptic Engine's backend is built on Spring Boot 4
with Kotlin, using Spring Security for authentication/authorization, Spring Data JPA for persistence,
Spring Mail for outbound email, and Spring Actuator for health and observability.

## 4.3 Spring Modulith

Spring Modulith is an extension of Spring Boot that helps developers build *modular monoliths* — a single
deployable application internally organized into well-defined modules with enforced boundaries [7]. Each
module (for example `crm`, `inventory`, `sharing`, `identity`) exposes only a small public API to the
others and keeps its internals private; Modulith can verify these boundaries at build time and even
generate documentation of the module structure. This gives the project the development simplicity of a
monolith while preserving the clean separation of concerns usually associated with microservices, and it
makes the codebase easier to understand and to maintain.

## 4.4 Nuxt and Vue

Vue is a progressive, component-based JavaScript framework for building user interfaces. Nuxt is a
full-featured framework built on top of Vue that adds file-based routing, server-side rendering, data
fetching, and a powerful module ecosystem [8]. The frontend of Synoptic Engine is a Nuxt 4 application
written with Vue 3's Composition API and TypeScript. It uses **Nuxt UI 4** (a component library on top of
Tailwind CSS 4) for the interface, **Pinia** for state management, **VueUse** for composable utilities,
and **Zod** for form validation. A TypeScript API client is generated automatically from the backend's
OpenAPI specification, so the frontend and backend stay in sync.

## 4.5 PostgreSQL

PostgreSQL is a powerful, open-source object-relational database system known for its reliability,
standards compliance, and rich feature set [9]. It is the data store for Synoptic Engine, accessed
through Hibernate/JPA, with schema changes managed by Flyway migrations. PostgreSQL was chosen
specifically because it provides native **Row-Level Security (RLS)** — the ability to attach security
policies directly to tables so that the database itself filters rows by tenant. This makes the database a
genuine second line of defence for tenant isolation, independent of the application code.

## 4.6 Languages

To build a full-stack application, several languages are essential. Different languages serve different
aspects of development.

### 4.6.1 Kotlin

Kotlin is the primary backend language. Created by JetBrains and running on the JVM, it offers concise
syntax, null safety, coroutines, and seamless interoperability with Java libraries. In this project
Kotlin is used to express the domain model, services, REST controllers, and the security and
tenant-isolation logic that form the heart of the backend.

### 4.6.2 TypeScript

TypeScript is a typed superset of JavaScript that compiles to plain JavaScript. It adds static types to
the language, catching many errors at compile time and making large frontends easier to maintain. The
entire Nuxt/Vue frontend is written in TypeScript, and the API client is generated as typed TypeScript
from the backend's OpenAPI schema, giving end-to-end type safety from the database to the browser.

### 4.6.3 SQL

SQL (Structured Query Language) is the language used to define and query relational data. In Synoptic
Engine, SQL is used in the Flyway migration scripts that create and evolve the schema, define indexes and
constraints, and — importantly — declare the Row-Level Security policies that enforce tenant isolation at
the database layer.

---

# Chapter 5 — Testing, Security and Maintenance

## 5.1 Methodology used for testing

A web-based testing methodology depends on the specific goals, requirements, and resources available.
After the application is thoroughly tested, it can be considered complete. Testing checks the
functionality, usability, security, compatibility, and performance of the system, and confirms that the
project can run in a real-time environment without breakdowns. For Synoptic Engine, testing was layered:
fast unit tests for individual services, integration tests that run against a real PostgreSQL database
using Testcontainers, and end-to-end browser tests that drive the live web client. A dedicated
two-tenant isolation test verifies that one tenant genuinely cannot read another's rows — the single most
important property of the system.

## 5.2 Testing Methods

Testing websites and applications for potential bugs before they are released to users is known as
application testing. Black-box testing and white-box testing are the two techniques used in software
testing, and the testing results are described from the perspective of these two methods [10].

## 5.3 Black Box Testing

Black-box testing examines the system from the outside, without access to its internal source code. Based
on the requirements and specifications, its purpose is to confirm that the system functions as intended
and generates the desired results for various inputs. It is applicable to functional, non-functional, and
regression testing, and uses techniques such as boundary value analysis, equivalence partitioning, state
transition testing, and error guessing. For Synoptic Engine, the end-to-end browser tests are a form of
black-box testing: they log in as a user and exercise the application's screens — creating leads, moving
pipeline stages, building quotes, performing mass operations, exporting data, and using cross-tenant
sharing — checking only the externally observable behaviour.

## 5.4 White Box Testing

With white-box testing, the tester has access to the source code and design documents along with full
knowledge of the application under test. The internal structure, design, and code are tested to confirm
input–output flow and to enhance design, usability, and security. This type of testing is also known as
"glass-box," "open-box," "transparent-box," "code-based," and "clear-box" testing, since the code is
visible during the process. In this project, the unit and integration tests are white-box tests: they
target specific services, repositories, security filters, and the tenant-context propagation logic with
knowledge of how they are implemented, exercising branch and path coverage of the critical isolation and
authentication code.

## 5.5 Security

Security — protecting systems and data against unauthorized access, modification, destruction, or
disruption — is the central concern of this project, because a multi-tenant platform shares one database
among many organizations. The system implements a **defense-in-depth** security model:

- **Authentication.** Users sign in with email and password (hashed with bcrypt) and receive a
  short-lived JSON Web Token; refresh sessions allow renewal. Optional **multi-factor authentication
  (TOTP)** adds a second factor, and **API keys** support programmatic access.
- **Authorization.** Role-based access control assigns permissions to roles and roles to users, so each
  action is checked against the caller's permissions.
- **Tenant isolation (the core defence).** Every tenant-owned table carries a `tenant_id`. A request's
  tenant is derived from its token and propagated through a `TenantContext`; a Hibernate `@Filter`
  automatically adds a `tenant_id = :current` predicate to every query, and PostgreSQL **Row-Level
  Security** policies enforce the same constraint *inside the database* as an independent backstop. If
  either layer were bypassed, the other still contains the breach.
- **Controlled sharing & auditing.** Cross-tenant access is only possible through an explicit, accepted
  relationship, and every such access is written to an immutable audit log.
- **Input & transport hardening.** Inputs are validated (Zod on the client, Bean Validation on the
  server), HTML is sanitized with jsoup, CSV exports are guarded against formula injection, and outbound
  URLs are validated to mitigate server-side request forgery.

As part of this project a full **production-readiness security audit** was conducted and documented
(see [`AUDIT_FINDINGS.md`](./AUDIT_FINDINGS.md)), cataloguing more than forty findings with severities,
locations, and minimal fixes. Conducting and recording such an audit is itself part of a mature security
methodology: it makes the system's residual risks explicit rather than hidden.

## 5.6 Maintenance

Maintenance is the process of keeping the software up-to-date, running smoothly, and performing well; it
is the last stage of the software-development life cycle. Software maintenance is needed when the
delivered software requires correction, bug fixes, performance improvements, or adaptation to changes in
the real world [11]. The modular architecture (Spring Modulith), the versioned database schema (Flyway),
the generated typed API client, and the automated test suite all make Synoptic Engine maintainable: a
change can be made within a single module, captured by a migration, and verified by tests before
release. There are four types of maintenance based on size and nature:

| Type | Description |
|---|---|
| Corrective Software Maintenance | Its main goal is to correct bugs, errors, and software flaws. |
| Adaptive Software Maintenance | Its objective is to update the software to reflect changes in the environment (e.g. new PostgreSQL or framework versions). |
| Perfective Software Maintenance | It focuses on features that improve the user experience. |
| Preventive Software Maintenance | It aims to reduce the probability of deterioration by refactoring and adapting the software (e.g. addressing audit findings before they cause incidents). |

**Table 4: Software Maintenance**

---

# Chapter 6 — Project Features and Functionalities

This chapter walks through the principal screens of the application as experienced by a signed-in user.
Every screen shown is automatically scoped to the user's tenant.

## 6.1 Dashboard

The dashboard is the landing page after login. It summarizes the tenant's sales position at a glance:
total leads, average lead value, won revenue, a pipeline funnel, revenue by source and by type, top
products, top customers, and recent and upcoming activities.

![Figure 5: Dashboard](screenshots/01-dashboard.png)

**Figure 5: Dashboard (Home)**

## 6.2 Authentication and Access Control

Users sign in with their email and password; accounts with multi-factor authentication enabled are then
prompted for a time-based code. The returned JSON Web Token scopes every subsequent request to the user's
tenant and carries the permissions granted by the user's roles. New organizations can self-register,
which provisions a fresh tenant and its first administrator, and a password-reset flow is provided.

## 6.3 Leads and Pipelines

Leads are the heart of the CRM. Each lead has a title, an expected value, a source and type, optional
links to a person/organization and products, and a position in a configurable pipeline. Leads can be
viewed as a sortable, filterable list or as a Kanban board where they are dragged between pipeline
stages; mass operations allow several leads to be moved or updated at once.

![Figure 6: Leads List](screenshots/02-leads.png)

**Figure 6: Leads List**

![Figure 7: Leads Pipeline](screenshots/02b-leads-kanban.png)

**Figure 7: Leads Pipeline (Kanban)**

The lead detail page shows the full record together with its activity timeline, tags, linked contacts,
and quotes.

![Figure 8: Lead Detail](screenshots/03-lead-detail.png)

**Figure 8: Lead Detail and Timeline**

## 6.4 Contacts

Contacts are split into **persons** and **organizations**, which can be linked to each other (a person
works at an organization). Both support tags and custom attributes, and each has a detail page showing
related leads, activities, and emails.

![Figure 9: Persons](screenshots/05-persons.png)

**Figure 9: Persons (Contacts)**

![Figure 10: Organizations](screenshots/07-organizations.png)

**Figure 10: Organizations**

## 6.5 Products and Quotes

Administrators maintain a product catalogue, each product carrying a SKU and a price. Users build quotes
from one or more products; the system computes the subtotal, tax, and total automatically, and the quote
can be tied to a lead.

![Figure 11: Products](screenshots/09-products.png)

**Figure 11: Products**

![Figure 12: Quote Detail](screenshots/12-quote-detail.png)

**Figure 12: Quote Detail**

## 6.6 Inventory

The inventory module tracks products across multiple warehouses. Users can view current stock per
warehouse, record stock movements (reserving and releasing quantities), perform inter-warehouse
transfers, and monitor a reorder view that highlights products below their reorder threshold.

![Figure 13: Warehouses](screenshots/14-warehouses.png)

**Figure 14: Warehouses**

![Figure 15: Inventory Stock](screenshots/16-inventory-stock.png)

**Figure 15: Inventory — Stock**

![Figure 16: Inventory Reorder](screenshots/18-inventory-reorder.png)

**Figure 16: Inventory — Reorder**

![Figure 17: Inventory Transfers](screenshots/19-inventory-transfers.png)

**Figure 17: Inventory — Transfers**

## 6.7 Activities

Activities — calls, meetings, tasks, and notes — can be logged against leads and contacts and are shown
both on the relevant record's timeline and in a central activities view, with due dates surfaced on the
dashboard.

![Figure 13b: Activities](screenshots/13-activities.png)

**Figure 13: Activities**

## 6.8 Mail

The mail module lets users compose and send email, keep drafts, and review sent items, all backed by
reusable email templates. (The inbox is populated only by inbound webhook parsing and is empty by design
in the demo dataset.)

![Figure 18: Mail Sent](screenshots/20b-mail-sent.png)

**Figure 18: Mail (Sent)**

## 6.9 Cross-Tenant Sharing

A distinctive feature of Synoptic Engine is **controlled cross-tenant sharing**. Two tenants can form an
explicit relationship (for example a partnership); once accepted, each can share selected records with
the other. Shared records appear in a dedicated "Shared with me" view, and every cross-tenant access is
written to a cross-tenant audit log that administrators can review.

![Figure 19: Sharing Relationships](screenshots/21-sharing-relationships.png)

**Figure 19: Sharing — Relationships**

![Figure 20: Shared with me](screenshots/22-sharing-shared-with-me.png)

**Figure 20: Sharing — Shared-with-me**

![Figure 21: Cross-Tenant Audit](screenshots/23-sharing-audit.png)

**Figure 21: Cross-Tenant Audit**

## 6.10 Settings and Administration

The settings area gives administrators full control over how their tenant works. Pipelines and their
stages, lead sources, lead types, tags, and custom attributes can all be configured without code. Users,
roles, and groups implement role-based access control, and the platform owner can manage tenants.

![Figure 22: Settings Pipelines](screenshots/24-settings-pipelines.png)

**Figure 22: Settings — Pipelines**

![Figure 23: Settings Users](screenshots/26-settings-users.png)

**Figure 23: Settings — Users**

![Figure 24: Settings Roles](screenshots/27-settings-roles.png)

**Figure 24: Settings — Roles**

Custom attributes implement an Entity–Attribute–Value (EAV) model, letting administrators add their own
fields to persons, organizations, leads, and products.

![Figure 25: Settings Attributes](screenshots/32-settings-attributes.png)

**Figure 25: Settings — Attributes (EAV)**

![Figure 26: Settings Tenants](screenshots/40-settings-tenants.png)

**Figure 26: Settings — Tenants**

## 6.11 Automation, Web-Forms and Webhooks

Beyond manual data entry, the platform offers automation. Administrators can build **automation
workflows** (rules that fire on events), publish public **web-forms** that capture leads directly into the
pipeline, register **webhooks** that notify external systems when leads or quotes change, and run **CSV
imports** to bulk-load data. Email templates make outbound communication consistent. Together these turn
the CRM from a passive record store into an active part of the sales process.

---

# Chapter 7 — Limitations, Future Work and Conclusion

## 7.1 Limitations

- ❖ The system is an MVP (minimum viable product) and a defense showcase; it has not yet been scaled or
  hardened for a hostile public, multi-tenant production deployment.
- ❖ Several production-hardening items identified in the audit remain as future work — most notably
  forcing PostgreSQL Row-Level Security with a dedicated non-owner database role in the production
  profile, and tightening multi-factor and rate-limiting flows for an internet-facing deployment.
- ❖ Inbound email parsing (the mail inbox) and some automation builders ship without demo content.
- ❢ There is no real-time/live update channel yet; the client fetches data on navigation rather than
  receiving server-pushed events.
- ❖ Reporting is limited to the built-in dashboard; there is no custom report builder or data-export
  pipeline beyond CSV.

## 7.2 Future Work

The platform has a promising path for further development:

- ❖ **Production multi-tenant hardening:** Force Row-Level Security with a separate runtime role, add a
  boot-time isolation self-check, and close the remaining audit findings so the platform is safe for a
  public, adversarial SaaS deployment.
- ❖ **Real-time updates:** Add WebSocket/Server-Sent-Events so dashboards, pipelines, and shared records
  update live without a page refresh.
- ❖ **Advanced analytics & reporting:** Provide a configurable report builder, forecasting, and richer
  cross-entity dashboards.
- ❖ **Deeper automation:** Expand the workflow engine with more triggers, conditions, and actions, and
  add scheduled jobs.
- ❖ **Integrations:** Two-way email/calendar sync, payment and accounting integrations, and a public API
  with first-class SDKs.
- ❖ **Mobile application:** A dedicated mobile client for on-the-go access to leads and activities.

## 7.3 Conclusion

The Synoptic Engine platform demonstrates that a secure, multi-tenant Customer Relationship Management
system can be designed and built end-to-end with modern, open-source technology. By providing a single
deployment that safely serves many organizations — each with leads, contacts, quotes, inventory, and
controlled cross-tenant collaboration — the application addresses the practical needs of sales teams
while confronting the hardest engineering problem of software-as-a-service: tenant isolation. Treating
that boundary as a first-class concern, defended in depth by both the application and the database, and
validating it with automated tests and a documented security audit, is the project's central
contribution. Future work focused on production hardening, real-time updates, deeper automation, and
integrations can further solidify the platform's value and carry it from a defense-ready MVP toward a
deployable commercial product.

---

# References

[1] Salesforce CRM. https://www.salesforce.com/

[2] Krayin CRM (open-source). https://krayincrm.com/

[3] Multi-tenant data architecture. https://learn.microsoft.com/azure/architecture/guide/multitenant/overview

[4] Software Development Life Cycle (SDLC). https://www.geeksforgeeks.org/software-development-life-cycle-sdlc/

[5] Graphical User Interface (GUI). https://www.techopedia.com/definition/5435/graphical-user-interface-gui

[6] Spring Boot. https://spring.io/projects/spring-boot

[7] Spring Modulith. https://spring.io/projects/spring-modulith

[8] Nuxt. https://nuxt.com/

[9] PostgreSQL. https://www.postgresql.org/

[10] Software testing methods. https://www.tutorialspoint.com/software_testing/software_testing_methods.htm

[11] Software maintenance. https://www.geeksforgeeks.org/software-engineering-software-maintenance/

[12] Kotlin programming language. https://kotlinlang.org/

[13] Vue.js. https://vuejs.org/

[14] Nuxt UI. https://ui.nuxt.com/

[15] Tailwind CSS. https://tailwindcss.com/

[16] PostgreSQL Row-Level Security. https://www.postgresql.org/docs/current/ddl-rowsecurity.html

[17] Hibernate ORM filters. https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#pc-filtering

[18] JSON Web Tokens (JWT). https://jwt.io/

[19] Flyway database migrations. https://www.red-gate.com/products/flyway/

[20] Pinia state management. https://pinia.vuejs.org/

[21] Zod schema validation. https://zod.dev/

[22] OpenAPI Specification. https://www.openapis.org/

[23] Testcontainers. https://testcontainers.com/

[24] Time-based One-Time Password (TOTP), RFC 6238. https://datatracker.ietf.org/doc/html/rfc6238
