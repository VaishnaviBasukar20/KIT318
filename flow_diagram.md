# MSP System Flow Diagram

## System Architecture

```mermaid
graph TD
    C[Client Application]
    MS[MSPServer]
    CH[ClientHandler]
    WH[WorkerHandler]
    Q[Job Queue]
    DB[(Job Database)]
    W[Worker]
    P[Python Executor]

    C --> MS
    MS --> CH
    CH --> DB
    CH --> Q
    Q --> WH
    WH --> W
    W --> P
    W --> WH
    W --> WH
    WH --> DB
    CH --> C
```

## Job Submission Flow

```mermaid
graph TD
    A[Client Connect] --> B[Login]
    B --> C[Submit Job]
    C --> D[Store Job]
    D --> E[Queue Job]
    E --> F[Assign to Worker]
    F --> G[Execute Python]
    G --> H[Job Complete]
    H --> I[Status Update]
```

## Worker Management Flow

```mermaid
graph TD
    A[Worker Connect] --> B[Send Heartbeat]
    B --> C[Check Jobs]
    C --> D{Jobs Available?}
    D -->|Yes| E[Assign Job]
    E --> F[Process Job]
    F --> C
    D -->|No| G[Wait]
    G --> B
```

## Error Handling Flow

```mermaid
graph TD
    A[Job Submission] --> B{Valid Paths?}
    B -->|No| C[Return Error]
    B -->|Yes| D[Execute Job]
    D --> E{Execution Success?}
    E -->|No| F[Retry Job]
    F -->|Retry Count < 3| D
    F -->|Retry Count >= 3| G[Mark Failed]
    E -->|Yes| H[Mark Complete]
```

## Data Flow

```mermaid
graph LR
    A[Client] --> B[MSP Server]
    B --> C[(Job Database)]
    B --> D[Job Queue]
    D --> E[Worker]
    E --> F[Python Script]
    E --> G[Data Folder]
    E --> H[Output Folder]
    E --> B
    B --> C
    B --> A
```

## Component Responsibilities

### MSP Server
- Handles client connections
- Manages worker nodes
- Maintains job queue
- Tracks job status
- Handles billing

### Worker
- Connects to server
- Executes Python scripts
- Reports job status
- Sends heartbeats
- Handles errors

### Client
- User authentication
- Job submission
- Status checking
- Billing information
- Job cancellation 
