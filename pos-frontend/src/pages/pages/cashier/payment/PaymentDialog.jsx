import React from "react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useSelector, useDispatch } from "react-redux";
import {
  selectCartItems,
  selectNote,
  selectPaymentMethod,
  selectSelectedCustomer,
  selectTotal,
  setCurrentOrder,
  setPaymentMethod,
  selectCouponCode,
} from "../../../Redux Toolkit/features/cart/cartSlice";
import { useToast } from "../../../components/ui/use-toast";
import { createOrder } from "../../../Redux Toolkit/features/order/orderThunks";
import { paymentMethods } from "./data";

const PaymentDialog = ({
  showPaymentDialog,
  setShowPaymentDialog,
  setShowReceiptDialog,
}) => {
  const paymentMethod = useSelector(selectPaymentMethod);
  const { toast } = useToast();
  const cart = useSelector(selectCartItems);
  const { branch } = useSelector((state) => state.branch);
  const { userProfile } = useSelector((state) => state.user);
  const dispatch = useDispatch();

  const selectedCustomer = useSelector(selectSelectedCustomer);
  const total = useSelector(selectTotal);
  const note = useSelector(selectNote);
  const appliedCouponCode = useSelector(selectCouponCode);

  const processPayment = async () => {
    if (cart.length === 0) {
      toast({
        title: "Empty Cart",
        description: "Please add items to cart before processing payment",
        variant: "destructive",
      });
      return;
    }

    if (!selectedCustomer) {
      toast({
        title: "Customer Required",
        description: "Please select a customer before processing payment",
        variant: "destructive",
      });
      return;
    }

    const resolvedBranchId = branch?.id || userProfile?.branchId;
    if (!resolvedBranchId) {
      toast({
        title: "Branch Missing",
        description: "Unable to determine active branch for this cashier",
        variant: "destructive",
      });
      return;
    }

    try {
      // Prepare order data matching Backend OrderDTO & OrderItemDTO structure
      const orderData = {
        totalAmount: total,
        branchId: resolvedBranchId,
        cashierId: userProfile?.id,
        customer: selectedCustomer || null,
        items: cart.map((item) => {
          const unitPrice = Number(item.sellingPrice ?? item.price ?? 0);
          return {
            productId: item.id,
            quantity: item.quantity,
            price: unitPrice * item.quantity,
          };
        }),
        paymentType: paymentMethod,
        note: note || "",
        couponCode: appliedCouponCode || null,
      };

      const createdOrder = await dispatch(createOrder(orderData)).unwrap();
      dispatch(setCurrentOrder(createdOrder));

      setShowPaymentDialog(false);
      setShowReceiptDialog(true);

      toast({
        title: "Order Created Successfully",
        description: `Order #${createdOrder.id} created and payment processed`,
      });
    } catch (error) {
      toast({
        title: "Order Creation Failed",
        description: error || "Failed to create order. Please try again.",
        variant: "destructive",
      });
    }
  };

  const handlePaymentMethod = (method) => dispatch(setPaymentMethod(method));

  return (
    <Dialog open={showPaymentDialog} onOpenChange={setShowPaymentDialog}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Payment</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div className="text-center">
            <div className="text-3xl font-bold text-green-600">
              ₹{Number(total || 0).toFixed(2)}
            </div>
            <p className="text-sm text-muted-foreground">Amount to be paid</p>
          </div>

          <div className="space-y-2">
            {paymentMethods.map((method) => (
              <Button
                key={method.key}
                variant={paymentMethod === method.key ? "default" : "outline"}
                className="w-full justify-start"
                onClick={() => handlePaymentMethod(method.key)}
              >
                {method.label}
              </Button>
            ))}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => setShowPaymentDialog(false)}>
            Cancel
          </Button>
          <Button onClick={processPayment}>Complete Payment</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PaymentDialog;
