# MSP System Flow Diagram

## System Architecture

```
+----------------+     +------------------+     +----------------+
|    Client      |     |   MSP Server     |     |    Worker      |
|                |     |                  |     |                |
| +------------+ |     | +--------------+ |     | +------------+ |
| | Application| |     | | MSPServer    | |     | | Worker     | |
| +------------+ |     | +--------------+ |     | +------------+ |
|                |     | +--------------+ |     | +------------+ |
|                |     | | ClientHandler| |     | | Python     | |
|                |     | +--------------+ |     | | Executor   | |
|                |     | +--------------+ |     | +------------+ |
|                |     | | WorkerHandler| |     |                |
|                |     | +--------------+ |     |                |
|                |     | +--------------+ |     |                |
|                |     | | Job Queue    | |     |                |
|                |     | +--------------+ |     |                |
|                |     | +--------------+ |     |                |
|                |     | | Job Database | |     |                |
|                |     | +--------------+ |     |                |
+----------------+     +------------------+     +----------------+
        |                        |                      |
        v                        v                      v
+----------------+     +------------------+     +----------------+
|   Connect      |     |  Handle Request  |     |  Execute Job   |
|   Login        |     |  Process Job     |     |  Send Status   |
|   Submit Job   |     |  Update Status   |     |  Heartbeat     |
+----------------+     +------------------+     +----------------+
```

## Job Submission Flow

```
Client                    Server                    Worker
  |                         |                         |
  |--- Connect ------------>|                         |
  |                         |                         |
  |--- Login -------------->|                         |
  |                         |                         |
  |<-- Login Success -------|                         |
  |                         |                         |
  |--- Submit Job --------->|                         |
  |                         |                         |
  |                         |--- Store Job ---------->|
  |                         |                         |
  |                         |--- Queue Job ---------->|
  |                         |                         |
  |                         |--- Assign Job --------->|
  |                         |                         |
  |                         |<-- Execute Python ------|
  |                         |                         |
  |                         |<-- Job Complete --------|
  |                         |                         |
  |<-- Status Update -------|                         |
  |                         |                         |
```

## Worker Management Flow

```
Worker                    Server                    Queue
  |                         |                         |
  |--- Connect ------------>|                         |
  |                         |                         |
  |--- Heartbeat ---------->|                         |
  |                         |                         |
  |                         |--- Check Queue -------->|
  |                         |                         |
  |                         |<-- Jobs Available ------|
  |                         |                         |
  |<-- Assign Job ----------|                         |
  |                         |                         |
  |--- Process Job -------->|                         |
  |                         |                         |
  |--- Send Status -------->|                         |
  |                         |                         |
  |--- Heartbeat ---------->|                         |
  |                         |                         |
```

## Error Handling Flow

```
Job Submission
      |
      v
Valid Paths? ----- No ----> Return Error
      |
     Yes
      |
      v
Execute Job
      |
      v
Success? ----- No ----> Retry Job (if < 3 attempts)
      |                  |
      |                  v
     Yes            Mark Failed
      |
      v
Mark Complete
```

## Data Flow

```
Client -----> MSP Server -----> Job Database
   ^              |
   |              v
   |           Job Queue
   |              |
   |              v
   |            Worker
   |              |
   |              v
   |        +-----+-----+
   |        |     |     |
   |        v     v     v
   |    Python  Data  Output
   |    Script  Folder Folder
   |        |     |     |
   |        +-----+-----+
   |              |
   |              v
   +-------------+
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
