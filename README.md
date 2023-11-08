# pagoPA Receipt-pdf-generator

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=pagopa_pagopa-receipt-pdf-generator&metric=alert_status)](https://sonarcloud.io/dashboard?id=pagopa_pagopa-receipt-pdf-generator)

Java Azure Functions that generate a PDF, from a receipt generated and saved on a CosmosDB previously
by [receipt-pdf-datastore](https://github.com/pagopa/pagopa-receipt-pdf-datastore), 
based on specific templates using the [PDF-engine function](https://github.com/pagopa/pagopa-pdf-engine)
and save it on an Azure Blob Storage.

---

## Summary 📖

- [Start Project Locally 🚀](#start-project-locally-)
    * [Run locally with Docker](#run-locally-with-docker)
        + [Prerequisites](#prerequisites)
        + [Run docker container](#run-docker-container)
    * [Run locally with Maven](#run-locally-with-maven)
        + [Prerequisites](#prerequisites-1)
        + [Set environment variables](#set-environment-variables)
        + [Run the project](#run-the-project)
    * [Test](#test)
- [Develop Locally 💻](#develop-locally-)
    * [Prerequisites](#prerequisites-2)
    * [Testing 🧪](#testing-)
        + [Unit testing](#unit-testing)
        + [Integration testing](#integration-testing)
        + [Performance testing](#performance-testing)
- [Contributors 👥](#contributors-)
    * [Maintainers](#maintainers)

---

## Start Project Locally 🚀

### Run locally with Docker

#### Prerequisites

- docker

#### Set environment variables

`docker build -t pagopa-receip-pdf-generator .`

`cp .env.example .env`

and replace in `.env` with correct values

#### Run docker container

then type :

`docker run -p 80:80 --env-file=./.env pagopa-receip-pdf-generator`

### Run locally with Maven

#### Prerequisites

- maven

#### Set environment variables

On terminal type:

`cp local.settings.json.example local.settings.json`

then replace env variables with correct values
(if there is NO default value, the variable HAS to be defined)

| VARIABLE                              | USAGE                                                                            |                     DEFAULT VALUE                      |
|---------------------------------------|----------------------------------------------------------------------------------|:------------------------------------------------------:|
| `RECEIPTS_STORAGE_CONN_STRING`        | Connection string to the Receipt Storage                                         |                                                        |
| `RECEIPT_QUEUE_TOPIC`                 | Topic name of the Receipt Queue                                                  |                                                        |
| `RECEIPT_QUEUE_MAX_RETRY`             | Number of retry to complete the generation process before being tagged as FAILED |                          "5"                           |
| `RECEIPT_QUEUE_TOPIC_POISON`          | Topic name of the Receipt Poison Queue                                           |                                                        |
| `BLOB_STORAGE_ACCOUNT_ENDPOINT`       | Endpoint to the Receipt Blob Storage                                             |                                                        |
| `BLOB_STORAGE_CONTAINER_NAME`         | Container name of the Receipt container in the Blob Storage                      |                                                        |
| `COSMOS_RECEIPTS_CONN_STRING`         | Connection string to the Receipt CosmosDB                                        |                                                        |
| `COSMOS_RECEIPT_SERVICE_ENDPOINT`     | Endpoint to the Receipt CosmosDB                                                 |                                                        |
| `COSMOS_RECEIPT_KEY`                  | Key to the Receipt CosmosDB                                                      |                                                        |
| `COSMOS_RECEIPT_DB_NAME`              | Database name of the Receipt database in CosmosDB                                |                                                        |
| `COSMOS_RECEIPT_CONTAINER_NAME`       | Container name of the Receipt container in CosmosDB                              |                                                        |
| `PDF_ENGINE_ENDPOINT`                 | Endpoint to the PDF engine                                                       |                                                        |
| `OCP_APIM_SUBSCRIPTION_KEY`           | Auth key for Azure to access the PDF Engine                                      |                                                        |
| `PDV_TOKENIZER_BASE_PATH`             | PDV Tokenizer API base path                                                      | "https://api.uat.tokenizer.pdv.pagopa.it/tokenizer/v1" |
| `PDV_TOKENIZER_SEARCH_TOKEN_ENDPOINT` | PDV Tokenizer API search token endpoint                                          |                    "/tokens/search"                    |
| `PDV_TOKENIZER_FIND_PII_ENDPOINT`     | PDV Tokenizer API find pii endpoint                                              |                    "/tokens/%s/pii"                    |
| `PDV_TOKENIZER_CREATE_TOKEN_ENDPOINT` | PDV Tokenizer API create token endpoint                                          |                       "/tokens"                        |
| `PDV_TOKENIZER_SUBSCRIPTION_KEY`      | Tokenizer API azure ocp apim subscription key                                    |                                                        |
| `TOKENIZER_APIM_HEADER_KEY`           | Tokenizer APIM header key                                                        |                       x-api-key                        |
| `AES_SECRET_KEY`                      | AES encryption secret key                                                        |                                                        |
| `AES_SALT`                            | AES encryption salt                                                              |                                                        |

> to doc details about AZ fn config
> see [here](https://stackoverflow.com/questions/62669672/azure-functions-what-is-the-purpose-of-having-host-json-and-local-settings-jso)

#### Run the project

`mvn clean package`

`mvn azure-functions:run`

### Test

`curl http://localhost:8080/info`

---

## Develop Locally 💻

### Prerequisites

- git
- maven
- jdk-11

### Testing 🧪

#### Unit testing

To run the **Junit** tests:

`mvn clean verify`

#### Integration testing

#### Performance testing

---

## Contributors 👥

Made with ❤️ by PagoPa S.p.A.

### Maintainers

See `CODEOWNERS` file