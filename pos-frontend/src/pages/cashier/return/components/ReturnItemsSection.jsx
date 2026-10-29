import React, { useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from "@/components/ui/select";
import { useToast } from "@/components/ui/use-toast";
import { useSelector, useDispatch } from "react-redux";
import {
  createRefund,
  getRefundsByBranch,
} from "../../../../Redux Toolkit/features/refund/refundThunks";

const returnReasons = [
  "Damaged product",
  "Wrong product",
  "Customer changed mind",
  "Product quality issue",
  "Pricing error",
  "Other",
];

const ReturnItemsSection = ({
  selectedOrder,
  setShowReceiptDialog,
  onRefundSuccess,
}) => {
  const { toast } = useToast();
  const { userProfile } = useSelector((state) => state.user);
  const { branch } = useSelector((state) => state.branch);
  const dispatch = useDispatch();

  const [returnReason, setReturnReason] = useState("");
  const [otherReason, setOtherReason] = useState("");
  const [refundMethod, setRefundMethod] = useState("original");
  const [submitting, setSubmitting] = useState(false);

  const processRefund = async () => {
    const finalReason =
      returnReason === "Other" ? otherReason.trim() : returnReason;
    if (!finalReason) {
      toast({
        title: "Missing Return Reason",
        description:
          "Please select or specify a return reason before processing the refund.",
        variant: "destructive",
      });
      return;
    }

    const resolvedMethod = (
      refundMethod === "original"
        ? selectedOrder.paymentType || "CASH"
        : refundMethod
    ).toUpperCase();

    const refundDTO = {
      orderId: selectedOrder.id,
      branchId: branch?.id,
      cashierId: userProfile?.id,
      reason: finalReason,
      refundMethod: resolvedMethod,
    };

    setSubmitting(true);
    try {
      await dispatch(createRefund(refundDTO)).unwrap();
      if (branch?.id) {
        dispatch(getRefundsByBranch(branch.id));
      }
      if (onRefundSuccess) {
        onRefundSuccess({ reason: finalReason, refundMethod: resolvedMethod });
      }
      setShowReceiptDialog(true);
      toast({
        title: "Refund Processed",
        description: `Refund of đ${selectedOrder.totalAmount} processed via ${resolvedMethod}`,
      });
    } catch (error) {
      toast({
        title: "Refund Failed",
        description: error || "Failed to process refund. Please try again.",
        variant: "destructive",
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="w-1/2 p-4 flex flex-col">
      <Card className="mt-4">
        <CardContent className="p-4">
          <div className="space-y-4">
            <div>
              <Label htmlFor="return-reason" className="mb-2 block">
                Return Reason
              </Label>
              <Select
                value={returnReason}
                onValueChange={(value) => setReturnReason(value)}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select a reason..." />
                </SelectTrigger>
                <SelectContent>
                  {returnReasons.map((reason) => (
                    <SelectItem key={reason} value={reason}>
                      {reason}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {returnReason === "Other" && (
              <div>
                <Label htmlFor="other-reason" className="mb-2 block">
                  Specify Reason
                </Label>
                <Textarea
                  id="other-reason"
                  placeholder="Please specify the return reason"
                  value={otherReason}
                  onChange={(e) => setOtherReason(e.target.value)}
                />
              </div>
            )}
            <div>
              <Label htmlFor="refund-method" className="mb-2 block">
                Refund Method
              </Label>
              <Select value={refundMethod} onValueChange={setRefundMethod}>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="Select refund method..." />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="original">
                    Original Payment Method (
                    {selectedOrder.paymentType || "CASH"})
                  </SelectItem>
                  <SelectItem value="CASH">Cash</SelectItem>
                  <SelectItem value="CARD">Card</SelectItem>
                  <SelectItem value="UPI">UPI</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="pt-4 border-t">
              <div className="flex justify-between text-lg font-semibold">
                <span>Total Refund Amount:</span>
                <span>đ{selectedOrder.totalAmount}</span>
              </div>
            </div>
            <Button
              className="w-full"
              onClick={processRefund}
              disabled={submitting}
            >
              {submitting ? "Processing Refund..." : "Process Refund"}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default ReturnItemsSection;
