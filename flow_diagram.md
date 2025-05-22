# MSP System Flow Diagram

## System Architecture

```mermaid
graph TB
    subgraph Client
        C[Client Application]
    end

    subgraph MSP_Server
        MS[MSPServer]
        CH[ClientHandler]
        WH[WorkerHandler]
        Q[Job Queue]
        DB[(Job Database)]
    end

    subgraph Worker_Node
        W[Worker]
        P[Python Executor]
    end

    C -->|1. Connect| MS
    MS -->|2. Handle Connection| CH
    CH -->|3. Store Job| DB
    CH -->|4. Queue Job| Q
    Q -->|5. Assign Job| WH
    WH -->|6. Send Job| W
    W -->|7. Execute| P
    W -->|8. Heartbeat| WH
    W -->|9. Job Complete| WH
    WH -->|10. Update Status| DB
    CH -->|11. Status Update| C
```

## Job Submission Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant MS as MSP Server
    participant W as Worker
    participant P as Python Executor

    C->>MS: 1. Connect
    C->>MS: 2. Login
    MS-->>C: 3. Login Success
    C->>MS: 4. Submit Job
    Note over MS: Store job details
    Note over MS: Add to queue
    MS->>W: 5. Assign Job
    W->>P: 6. Execute Python
    P-->>W: 7. Execution Result
    W->>MS: 8. Job Complete
    MS-->>C: 9. Status Update
```

## Worker Management Flow

```mermaid
sequenceDiagram
    participant MS as MSP Server
    participant W as Worker
    participant Q as Job Queue

    W->>MS: 1. Connect
    loop Every 5 seconds
        W->>MS: 2. Send Heartbeat
    end
    loop While Jobs Available
        MS->>W: 3. Assign Job
        W->>MS: 4. Job Status
    end
    Note over MS,Q: If Queue Length > Threshold
    MS->>MS: 5. Create New Worker
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
    I[Worker Failure] --> J[Remove Worker]
    J --> K[Resubmit Job]
    K --> D
```

## Data Flow

```mermaid
graph LR
    A[Client] -->|1. Job Request| B[MSP Server]
    B -->|2. Store Job| C[(Job Database)]
    B -->|3. Queue Job| D[Job Queue]
    D -->|4. Assign| E[Worker]
    E -->|5. Read| F[Python Script]
    E -->|6. Read| G[Data Folder]
    E -->|7. Write| H[Output Folder]
    E -->|8. Status| B
    B -->|9. Update| C
    B -->|10. Response| A
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
