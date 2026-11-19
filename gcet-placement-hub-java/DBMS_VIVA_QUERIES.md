# GCET PLACEMENT HUB — DBMS Faculty Evaluation & Viva Query Pack

**Course:** Database Management Systems PBL Evaluation  
**Institution:** Geethanjali College of Engineering & Technology (UGC Autonomous, Hyderabad)  
**Database Engine:** MongoDB 7.x / 8.x (MongoDB Atlas Cloud M0 Cluster / Local Community Fallback)  
**Database Name:** `gcet_placement_hub`  
**Application Architecture:** Java 17 Desktop Swing Client + `mongodb-driver-sync` 5.x  

---

## 1. Quick Atlas Web & Compass Connection Guide

### Option A: MongoDB Atlas Data Explorer (Web Browser)
1. Log in to [MongoDB Atlas](https://cloud.mongodb.com/).
2. Navigate to **Database** $\rightarrow$ **Browse Collections**.
3. Under database **`gcet_placement_hub`**, inspect all 10 verified collections:
   - `students`, `placement_drives`, `applications`, `users`, `companies`, `faculty`, `skills`, `approvals`, `announcements`, `audit_logs`.
4. Click the **Validation** tab on `students` or `applications` to show the professor the live `$jsonSchema` rules.
5. Click the **Indexes** tab to show compound and text indexes.

### Option B: MongoDB Compass (GUI)
1. Open MongoDB Compass.
2. Paste your connection string (from `.env`):
   ```
   mongodb+srv://<username>:<password>@cluster0.xxxxx.mongodb.net/gcet_placement_hub?retryWrites=true&w=majority
   ```
3. Connect and explore schemas, documents, and run pipeline aggregations visually.

### Option C: `mongosh` (Command-Line Shell)
```bash
mongosh "mongodb+srv://<username>:<password>@cluster0.xxxxx.mongodb.net/gcet_placement_hub"
```
Switch to the database:
```javascript
use gcet_placement_hub
show collections
db.stats()
```

---

## 2. In-App Live Evaluation Console (1-Click Demo)

You do **not** need external tools during the viva presentation. The desktop application has a built-in **DBMS Faculty Console**:

1. In the application sidebar, click **"⚡ DBMS Faculty Console"** (or click the **Atlas Connected** pill in the top header).
2. **Top Action Bar:**
   - Click **"⚡ Live Cloud Write-and-Read Test"**: Executes an atomic insert into Atlas `audit_logs`, reads it back immediately, and displays write latency, read latency, and the persisted BSON document.
   - Click **"🛡️ Test $jsonSchema Violation Rejection"**: Attempts an invalid student insert (CGPA 14.5, negative backlogs) and displays the MongoDB server rejection dialog showing Error Code **121 (`DocumentValidationFailure`)**.
3. **Tab 1 — Collections & Indexes:** Live document counts and active indexes across all 10 collections.
4. **Tab 2 — Live BSON Samples:** Inspect real embedded vs referenced JSON documents.
5. **Tab 3 — Aggregation & EXPLAIN:** Run all 5 complex aggregation pipelines and view `.explain("executionStats")`.
6. **Tab 4 — $jsonSchema Rules:** Programmatic validation contracts enforced on `students`, `applications`, etc.
7. **Tab 5 — Custom Query Console:** Select any preset or type custom MongoDB JSON filters, execute find queries live, and generate execution plans showing `IXSCAN`.

---

## 3. Copy-Pasteable `mongosh` / Compass Queries

### 3.1 Basic CRUD & Dot-Notation on Embedded Sub-Documents

#### Query 1: Filter students by embedded academic metrics (CGPA $\ge$ 8.5 & 0 Backlogs)
```javascript
// Dot-notation navigation inside embedded 'academicMetrics' sub-document
db.students.find(
  {
    "academicMetrics.cgpa": { $gte: 8.5 },
    "academicMetrics.backlogs": 0
  },
  {
    rollNumber: 1,
    fullName: 1,
    branch: 1,
    "academicMetrics.cgpa": 1,
    "academicMetrics.backlogs": 1
  }
).sort({ "academicMetrics.cgpa": -1 })
```

#### Query 2: Filter drives by CTC and Open Status
```javascript
db.placement_drives.find(
  {
    ctcLpa: { $gte: 10.0 },
    status: "OPEN"
  },
  {
    companyName: 1,
    jobRole: 1,
    ctcLpa: 1,
    requiredSkills: 1,
    driveDate: 1
  }
).sort({ ctcLpa: -1 })
```

---

### 3.2 Embedded Array Queries (`$elemMatch`, `$in`, `$all`)

#### Query 3: Match students with specific embedded skill and proficiency level
```javascript
// Demonstrates $elemMatch ensuring BOTH conditions match on the SAME skill object
db.students.find(
  {
    skills: {
      $elemMatch: {
        name: "Python",
        rating: { $gte: 4 }
      }
    }
  },
  {
    rollNumber: 1,
    fullName: 1,
    branch: 1,
    skills: 1
  }
)
```

#### Query 4: Match drives requiring specific technologies
```javascript
// Matches drives where requiredSkills array contains at least one of Java or Spring Boot
db.placement_drives.find(
  {
    requiredSkills: { $in: ["Java", "Spring Boot"] }
  },
  {
    companyName: 1,
    jobRole: 1,
    requiredSkills: 1,
    eligibleBranches: 1
  }
)
```

#### Query 5: Find applications with an interview stage in their audit trail
```javascript
// Queries inside the embedded stageHistory array
db.applications.find(
  {
    "stageHistory.stage": "TECHNICAL_INTERVIEW"
  },
  {
    studentName: 1,
    companyName: 1,
    currentStage: 1,
    status: 1,
    stageHistory: 1
  }
)
```

---

### 3.3 Atomic Embedded Array Updates (`$push`, `$pull`)

#### Query 6: Append a new interview milestone to an application ($push)
```javascript
db.applications.updateOne(
  { _id: ObjectId("<APPLICATION_ID>") },
  {
    $push: {
      stageHistory: {
        stage: "OFFERED",
        enteredAt: new Date(),
        updatedBy: "tpo.gcet",
        notes: "Offered 14 LPA Package post HR round"
      }
    },
    $set: {
      currentStage: "OFFERED",
      status: "OFFERED",
      updatedAt: new Date()
    }
  }
)
```

#### Query 7: Add an embedded project to a student profile ($push)
```javascript
db.students.updateOne(
  { rollNumber: "22R11A0501" },
  {
    $push: {
      projects: {
        _id: new ObjectId(),
        title: "AI Security Firewall",
        description: "RAG-powered LLM security gateway",
        technologies: ["Python", "Go", "MongoDB"],
        githubUrl: "https://github.com/emmanuelmj/llm-firewall",
        liveUrl: "",
        startDate: "2026-01-10",
        endDate: "2026-05-15"
      }
    }
  }
)
```

---

### 3.4 Full-Text Search Queries

#### Query 8: Full-text search across companies collection
```javascript
// Uses the compound text index on companyName, description, and industry
db.companies.find(
  { $text: { $search: "Amazon Cloud Software" } },
  { score: { $meta: "textScore" }, companyName: 1, industry: 1, tier: 1 }
).sort({ score: { $meta: "textScore" } })
```

---

## 4. Multi-Stage MongoDB Aggregation Pipelines

### Pipeline 1: Branch Placement KPI Analytics ($group, $avg, $sum, $sort)
*Source:* `DashboardService.getBranchAggregationPipeline()`  
*Purpose:* Computes total students, placed count, unplaced count, placement rate %, and average CGPA per engineering branch in a single database pass.

```javascript
db.students.aggregate([
  {
    $group: {
      _id: "$branch",
      totalStudents: { $sum: 1 },
      placedCount: {
        $sum: {
          $cond: [{ $eq: ["$placementStatus", "PLACED"] }, 1, 0]
        }
      },
      unplacedCount: {
        $sum: {
          $cond: [{ $eq: ["$placementStatus", "UNPLACED"] }, 1, 0]
        }
      },
      avgCgpa: { $avg: "$academicMetrics.cgpa" }
    }
  },
  {
    $project: {
      branch: "$_id",
      totalStudents: 1,
      placedCount: 1,
      unplacedCount: 1,
      placementRatePct: {
        $round: [
          {
            $multiply: [
              { $divide: ["$placedCount", { $max: ["$totalStudents", 1] }] },
              100
            ]
          },
          1
        ]
      },
      avgCgpa: { $round: ["$avgCgpa", 2] }
    }
  },
  { $sort: { placementRatePct: -1, totalStudents: -1 } }
])
```

---

### Pipeline 2: Placement Drive Eligibility Screening ($match, $project, $sort)
*Source:* `PlacementEngine.buildEligibilityPipeline(drive)`  
*Purpose:* Matches student documents against drive eligibility criteria (min CGPA, max backlogs, graduating year, eligible branches) without needing SQL joins.

```javascript
db.students.aggregate([
  {
    $match: {
      approvalStatus: "APPROVED",
      placementStatus: { $ne: "PLACED" },
      branch: { $in: ["CSE", "IT", "ECE"] },
      "academicMetrics.graduationYear": 2026,
      "academicMetrics.cgpa": { $gte: 7.5 },
      "academicMetrics.backlogs": { $lte: 0 }
    }
  },
  {
    $project: {
      rollNumber: 1,
      fullName: 1,
      email: 1,
      branch: 1,
      cgpa: "$academicMetrics.cgpa",
      backlogs: "$academicMetrics.backlogs",
      skills: 1
    }
  },
  { $sort: { cgpa: -1 } }
])
```

---

### Pipeline 3: Skill-Gap Analysis Pipeline ($setIntersection, $setDifference, $lookup)
*Source:* `PlacementEngine.buildSkillGapPipeline(drive)`  
*Purpose:* Performs set algebra inside the database engine comparing a student's embedded skills against the drive's required skills.

```javascript
db.students.aggregate([
  {
    $match: {
      approvalStatus: "APPROVED",
      placementStatus: { $ne: "PLACED" },
      branch: { $in: ["CSE", "IT"] }
    }
  },
  {
    $project: {
      rollNumber: 1,
      fullName: 1,
      branch: 1,
      cgpa: "$academicMetrics.cgpa",
      studentSkillNames: {
        $map: {
          input: "$skills",
          as: "s",
          in: "$$s.name"
        }
      }
    }
  },
  {
    $project: {
      rollNumber: 1,
      fullName: 1,
      branch: 1,
      cgpa: 1,
      matchedSkills: {
        $setIntersection: ["$studentSkillNames", ["Java", "Spring Boot", "MongoDB", "Docker"]]
      },
      missingSkills: {
        $setDifference: [["Java", "Spring Boot", "MongoDB", "Docker"], "$studentSkillNames"]
      }
    }
  },
  {
    $project: {
      rollNumber: 1,
      fullName: 1,
      branch: 1,
      cgpa: 1,
      matchedCount: { $size: "$matchedSkills" },
      missingCount: { $size: "$missingSkills" },
      readinessPct: {
        $round: [
          {
            $multiply: [
              { $divide: [{ $size: "$matchedSkills" }, 4] },
              100
            ]
          },
          1
        ]
      },
      missingSkills: 1
    }
  },
  { $sort: { readinessPct: -1, cgpa: -1 } }
])
```

---

### Pipeline 4: CTC Distribution Analysis ($bucket)
*Source:* `DashboardService.getCtcDistributionPipeline()`  
*Purpose:* Automatically bins placed packages into salary tiers using MongoDB's `$bucket` stage.

```javascript
db.students.aggregate([
  {
    $match: {
      placementStatus: "PLACED",
      placedPackageLpa: { $gt: 0 }
    }
  },
  {
    $bucket: {
      groupBy: "$placedPackageLpa",
      boundaries: [0, 4.0, 7.0, 10.0, 15.0, 25.0, 100.0],
      default: "Other",
      output: {
        count: { $sum: 1 },
        averagePackage: { $avg: "$placedPackageLpa" },
        students: { $push: "$rollNumber" }
      }
    }
  }
])
```

---

### Pipeline 5: Application Funnel Analytics ($facet, $unwind)
*Source:* `DashboardService.getApplicationFunnelPipeline()`  
*Purpose:* Uses `$facet` to execute multi-pipeline parallel aggregations on application stages and stage transition frequency.

```javascript
db.applications.aggregate([
  {
    $facet: {
      byCurrentStage: [
        { $group: { _id: "$currentStage", count: { $sum: 1 } } },
        { $sort: { count: -1 } }
      ],
      stageHistoryBreakdown: [
        { $unwind: "$stageHistory" },
        { $group: { _id: "$stageHistory.stage", hits: { $sum: 1 } } },
        { $sort: { hits: -1 } }
      ],
      totalApplicants: [
        { $group: { _id: "$studentId" } },
        { $count: "uniqueStudents" }
      ]
    }
  }
])
```

---

## 5. Index Verification & Execution Plan Analysis (`.explain()`)

Demonstrating query plan optimization is a major scoring component in DBMS evaluations.

### 5.1 Query Plan on Indexed Student Search
```javascript
db.students.find(
  { "academicMetrics.cgpa": { $gte: 8.5 } }
).explain("executionStats")
```

### How to Explain the Output to the Professor:
1. **Winning Plan Stage:** Look for `IXSCAN` (Index Scan) vs `COLLSCAN` (Collection Scan).
   - `IXSCAN`: The query engine used the index `idx_students_branch_cgpa` or `academicMetrics.cgpa`.
   - `COLLSCAN`: Full table scan (undesirable for large tables).
2. **Key Metrics to Highlight:**
   - `executionTimeMillis`: Milliseconds spent executing the query (typically 0-2 ms with indexes).
   - `totalKeysExamined`: Number of index keys read.
   - `totalDocsExamined`: Number of documents fetched from disk. If `totalDocsExamined == nReturned`, the index selectivity is 100% optimal!
   - `nReturned`: Exact matching documents returned to the client.

---

## 6. Server-Side `$jsonSchema` Validation Rejection Test

To prove to the professor that MongoDB is **not** an ungoverned "schemaless free-for-all", demonstrate server-side validation rejection.

### The Invalid Document (Violates multiple constraints):
```javascript
db.students.insertOne({
  fullName: "Invalid Student Demo",
  email: "invalid-email-format",              // Violates email regex pattern
  branch: "CSE",
  academicMetrics: {
    cgpa: 14.5,                               // Violates maximum: 10.0
    backlogs: -2,                              // Violates minimum: 0
    graduationYear: 2026
  },
  approvalStatus: "NON_EXISTENT_STATUS",      // Violates allowed enum values
  placementStatus: "UNPLACED",
  createdAt: new Date()
  // Missing required property: "rollNumber"
})
```

### Expected MongoDB Server Response:
```json
{
  "ok": 0,
  "errmsg": "Document failed validation",
  "code": 121,
  "codeName": "DocumentValidationFailure"
}
```
**Talking Point:** The MongoDB engine blocks this write at the database tier before any data is written to disk. The Java application handles Error Code 121 and displays the exact validation breakdown to the user.

---

## 7. Viva Q&A Talking Points (High-Scoring Answers)

### Q1: Why is a Document-Oriented Architecture (MongoDB) optimal for a College Placement System?
> **Answer:** In GCET's placement domain, student profiles and application records are deeply hierarchical. A student maintains multiple verified skills (with ratings), certifications, projects, internships, and semester-wise academic metrics.
> - **Zero-Join Reads (< 5ms):** The entire student profile is stored as a single cohesive BSON document aggregate. A single read operation (`db.students.findOne({ rollNumber: ... })`) retrieves the entire portfolio with zero joins, reducing network round-trips and memory overhead.
> - **Atomic Profile Updates:** Modifying a student's skills, projects, or application status uses atomic array operators (`$push`, `$pull`, and positional `$`), executing in a single atomic database operation.
> - **Native Aggregations:** Complex institutional metrics (branch placement ratios, eligibility screening, skill gap set algebra) execute inside MongoDB's C++ aggregation engine rather than requiring heavy application-layer transformations.

### Q2: Why did you EMBED skills and projects, but REFERENCE companies and placement drives?
> **Answer:** We followed the canonical MongoDB modeling rule: **"Data that is accessed together and has 1-to-few bounded cardinality should be embedded; data with independent lifecycles or 1-to-many unbounded cardinality should be referenced."**
> 1. **Embedded (1-to-Few, Bounded):**
>    - `skills[]`, `certifications[]`, and `projects[]` belong exclusively to one student. A student will have at most 20-30 skills and 5-10 projects throughout B.Tech. This comfortably stays below the 16MB BSON document limit and eliminates joins.
>    - `stageHistory[]` is embedded in `applications` as an audit trail of stage transitions (Applied $\rightarrow$ Shortlisted $\rightarrow$ Interview $\rightarrow$ Selected).
> 2. **Referenced (Independent Lifecycle, 1-to-Many):**
>    - `companies` and `placement_drives` exist independently of any individual student. Hundreds of students apply to a single drive. Embedding student applications inside the drive document would risk hitting the 16MB document boundary and cause concurrency conflicts during concurrent student applications.
>    - Hence, `applications` stores `ObjectId("...")` references to both `studentId` and `driveId`, backed by a unique compound index: `uq_applications_student_drive`.

### Q3: How do you prevent duplicate applications by the same student for the same drive?
> **Answer:** We enforce a **Unique Compound Index** at the database level:
> ```javascript
> db.applications.createIndex(
>   { studentId: 1, driveId: 1 },
>   { unique: true, name: "uq_applications_student_drive" }
> )
> ```
> Even if two concurrent application requests are submitted at the exact same millisecond, the database engine enforces idempotency and throws a Duplicate Key Exception (Error Code 11000).

### Q4: Does MongoDB support ACID transactions?
> **Answer:** Yes.
> 1. **Single-Document ACID:** In MongoDB, all write operations on a single document are atomic by default. Because our student profile embeds skills and projects, updating a profile is inherently ACID without requiring multi-document locks.
> 2. **Multi-Document ACID Transactions:** For operations that span multiple collections (such as updating an application stage and simultaneously recording an entry in `audit_logs`), MongoDB supports multi-document distributed transactions across replica sets and sharded clusters.

### Q5: How do you handle schema consistency if MongoDB is NoSQL?
> **Answer:** We implemented **Programmatic `$jsonSchema` Validation** via `MongoSchemaInitializer.java`. When collections are created, JSON Schema validators are registered with `validationAction: "error"` and `validationLevel: "moderate"`. The database strictly validates required fields, BSON data types (strings, doubles, dates, arrays), regex patterns (e.g., GCET roll number and email formats), and enum sets before allowing any insert or update.
