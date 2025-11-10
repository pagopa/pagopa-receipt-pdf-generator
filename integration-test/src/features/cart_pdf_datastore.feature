Feature: Cart receipt generation triggered by a Queue message containing BizEvent data

    Scenario: Successful PDF generation and status update for an INSERTED cart receipt
        Given a cart with id "cart-receipt-success-1" and status "INSERTED" stored into cart datastore
        And a random biz event with id "cart-receipt-success-1" enqueued on cart queue
        When the PDF receipt has been properly generate from biz event after 20000 ms
        Then the cart datastore returns the cart
        And the receipt has eventId "cart-receipt-success-1"
        And the receipt has not the status "INSERTED"
        And the receipt has the status "GENERATED"
        And the blob storage has the PDF document

    Scenario: Successful PDF generation and status update for a RETRY cart receipt
        Given a cart with id "cart-receipt-success-2" and status "RETRY" stored into cart datastore
        And a random biz event with id "cart-receipt-success-2" enqueued on cart queue
        When the PDF receipt has been properly generate from biz event after 20000 ms
        Then the cart datastore returns the cart
        And the receipt has eventId "cart-receipt-success-2"
        And the receipt has not the status "RETRY"
        And the receipt has the status "GENERATED"
        And the blob storage has the PDF document

    Scenario: Cart receipt is discarded if status is not INSERTED or RETRY
      Given a cart with id "cart-receipt-success-3" and status "FAILED" stored into cart datastore
      And a random biz event with id "cart-receipt-success-3" enqueued on cart queue
      When the PDF receipt has been properly generate from biz event after 20000 ms
      Then the cart datastore returns the cart
      And the receipt has eventId "cart-receipt-success-3"
      And the receipt has not the status "FAILED"
