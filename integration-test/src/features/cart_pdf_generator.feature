Feature: Cart receipt generation triggered by a Queue message containing BizEvent data

    Scenario: Successful PDF generation and status update for an INSERTED cart receipt
        Given a cart with id "cart-receipt-success-1" and eventId "tr-success-1" and status "INSERTED" stored into cart datastore
        And random biz events for cart with id "cart-receipt-success-1" and transaction id "tr-success-1" enqueued on cart queue
        When the PDFs have been properly generate from cart after 30000 ms
        Then the cart datastore returns the cart
        And the receipt has eventId "tr-success-1"
        And the receipt has not the status "INSERTED"
        And the receipt has not the status "FAILED"
        And the blob storage has the PDF document for payer

    Scenario: Successful PDF generation and status update for a RETRY cart receipt
        Given a cart with id "cart-receipt-success-2" and eventId "tr-success-2" and status "RETRY" stored into cart datastore
        And random biz events for cart with id "cart-receipt-success-2" and transaction id "tr-success-2" enqueued on cart queue
        When the PDFs have been properly generate from cart after 30000 ms
        Then the cart datastore returns the cart
        And the receipt has eventId "tr-success-2"
        And the receipt has not the status "RETRY"
        And the receipt has not the status "FAILED"
        And the blob storage has the PDF document for payer

    Scenario: Cart receipt is discarded if status is not INSERTED or RETRY
        Given a cart with id "cart-receipt-success-3" and eventId "tr-success-3" and status "FAILED" stored into cart datastore
        And random biz events for cart with id "cart-receipt-success-3" and transaction id "tr-success-3" enqueued on cart queue
        When the cart is discarded from generation after 30000 ms
        Then the cart datastore returns the cart
        And the receipt has eventId "tr-success-3"
        And the receipt has the status "FAILED"

    Scenario: a biz event stored on cart-receipts-message-error is enqueued on receipt queue that trigger the PDF receipt generation
        Given a cart with id "cart-receipt-retry-1" and eventId "tr-retry-1" and status "RETRY" stored into cart datastore
        And a error cart with id "cart-receipt-retry-1" and transactionId "tr-retry-1" stored into cart-receipts-message-error datastore with status REVIEWED
        When the PDFs have been properly generate from cart after 30000 ms
        Then the cart datastore returns the cart
        And the receipt has eventId "tr-retry-1"
        And the receipt has not the status "TO_REVIEW"
        And the receipt has not the status "NOT_QUEUE_SENT"
        And the receipt has not the status "INSERTED"
        And the blob storage has the PDF document for payer
        When the error cart has been properly stored on cart-receipts-message-error datastore after 0 ms
        Then the cart-receipts-message-error datastore returns the error receipt
        And the error cart has the status "REQUEUED"
  