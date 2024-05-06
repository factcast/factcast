## Usage

### Prerequisites
- provide dynamo client
- provide lockTable
    - this table is used for both write locks and factStreamPosition
- provide projectionTable(s)
    - table(s) for saving the projection data