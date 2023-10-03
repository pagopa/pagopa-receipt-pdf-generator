# sh run_performance_test.sh <local|dev|uat|prod> <load|stress|spike|soak|...> <script-name> <db-name> <biz-event-cosmos-subkey> <receipts-cosmos-subkey>

ENVIRONMENT=$1
TYPE=$2
SCRIPT=$3
DB_NAME=$4
RECEIPT_QUEUE_SUBSCRIPTION_KEY=$5
RECEIPT_COSMOS_DB_SUBSCRIPTION_KEY=$6
PROCESS_TIME=$7

if [ -z "$ENVIRONMENT" ]
then
  echo "No env specified: sh run_performance_test.sh <local|dev|uat|prod> <load|stress|spike|soak|...> <script-name> <db-name> <receipts-queue-subkey> <receipts-cosmos-subkey> <process_time>"
  exit 1
fi

if [ -z "$TYPE" ]
then
  echo "No test type specified: sh run_performance_test.sh <local|dev|uat|prod> <load|stress|spike|soak|...> <script-name> <db-name> <receipts-queue-subkey> <receipts-cosmos-subkey> <process_time>"
  exit 1
fi
if [ -z "$SCRIPT" ]
then
  echo "No script name specified: sh run_performance_test.sh <local|dev|uat|prod> <load|stress|spike|soak|...> <script-name> <db-name> <receipts-queue-subkey> <receipts-cosmos-subkey> <process_time>"
  exit 1
fi

export env=${ENVIRONMENT}
export type=${TYPE}
export script=${SCRIPT}
export db_name=${DB_NAME}
export receipts_queue_key=${RECEIPT_QUEUE_SUBSCRIPTION_KEY}
export receipts_cosmos_key=${RECEIPT_COSMOS_DB_SUBSCRIPTION_KEY}
export process_time=${PROCESS_TIME}

stack_name=$(cd .. && basename "$PWD")
docker compose -p "${stack_name}-k6" up -d --remove-orphans --force-recreate --build
docker logs -f k6
docker stop nginx
