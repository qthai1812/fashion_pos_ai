import React, { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { useToast } from "@/components/ui/use-toast";
import {
  Banknote,
  CreditCard,
  Smartphone,
  Star,
  Sparkles,
  Gift,
  Loader2,
  CheckCircle2,
  User,
} from "lucide-react";
import {
  selectCartItems,
  selectCouponCode,
  selectDiscountAmount,
  selectNote,
  selectPaymentMethod,
  selectSelectedCustomer,
  selectSubtotal,
  selectTax,
  selectTotal,
  setCurrentOrder,
  setPaymentMethod,
  resetOrder,
} from "../../../Redux Toolkit/features/cart/cartSlice";
import { createOrder } from "../../../Redux Toolkit/features/order/orderThunks";
import { getInventoryByBranch } from "../../../Redux Toolkit/features/inventory/inventoryThunks";

const VND_PER_POINT_EARNED = 10000; // 10.000₫ = +1 điểm
const VND_PER_POINT_REDEEMED = 1000; // 1 điểm = 1.000₫

const PAYMENT_METHODS = [
  {
    id: "CASH",
    label: "Tiền mặt (Cash)",
    icon: Banknote,
    activeClass:
      "border-emerald-600 bg-emerald-50 text-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-200",
  },
  {
    id: "CARD",
    label: "Thẻ (Card)",
    icon: CreditCard,
    activeClass:
      "border-blue-600 bg-blue-50 text-blue-800 dark:bg-blue-950/50 dark:text-blue-200",
  },
  {
    id: "UPI",
    label: "Chuyển khoản / QR (UPI)",
    icon: Smartphone,
    activeClass:
      "border-purple-600 bg-purple-50 text-purple-800 dark:bg-purple-950/50 dark:text-purple-200",
  },
];

const PaymentDialog = ({
  showPaymentDialog,
  setShowPaymentDialog,
  setShowReceiptDialog,
}) => {
  const dispatch = useDispatch();
  const { toast } = useToast();

  const cart = useSelector(selectCartItems);
  const subTotal = useSelector(selectSubtotal);
  const taxAmount = useSelector(selectTax);
  const discountAmount = useSelector(selectDiscountAmount);
  const total = useSelector(selectTotal);
  const paymentMethod = useSelector(selectPaymentMethod);
  const selectedCustomer = useSelector(selectSelectedCustomer);
  const note = useSelector(selectNote);
  const couponCode = useSelector(selectCouponCode);

  const { userProfile } = useSelector((state) => state.user);
  const { branch } = useSelector((state) => state.branch);

  const [processing, setProcessing] = useState(false);
  const [cashReceived, setCashReceived] = useState("");

  const normalizedSelected = (paymentMethod || "CASH").toUpperCase();
  const resolvedBranchId =
    branch?.id || userProfile?.branchId || userProfile?.branch?.id;

  // Tính toán thông tin điểm thưởng dự kiến cho khách hàng
  const availablePoints = Math.max(
    0,
    Number(selectedCustomer?.loyaltyPoints || 0),
  );
  const isRedeemingPoints = couponCode === "POINTS";
  const pointsUsed = isRedeemingPoints
    ? Math.min(
        availablePoints,
        Math.floor(Math.max(0, subTotal || 0) / VND_PER_POINT_REDEEMED),
      )
    : 0;
  const earnedPoints = selectedCustomer
    ? Math.floor(Math.max(0, total || 0) / VND_PER_POINT_EARNED)
    : 0;
  const projectedPoints = Math.max(
    0,
    availablePoints - pointsUsed + earnedPoints,
  );

  // Tính tiền thừa nếu thanh toán tiền mặt
  const numericCashReceived = parseFloat(cashReceived) || 0;
  const changeAmount =
    normalizedSelected === "CASH" && numericCashReceived > total
      ? numericCashReceived - total
      : 0;

  const handleSelectPaymentMethod = (methodId) => {
    dispatch(setPaymentMethod(methodId));
  };

  const handlePayment = async () => {
    if (!cart || cart.length === 0) {
      toast({
        title: "Giỏ hàng trống",
        description:
          "Vui lòng thêm sản phẩm vào giỏ hàng trước khi thanh toán.",
        variant: "destructive",
      });
      return;
    }

    if (!resolvedBranchId) {
      toast({
        title: "Thiếu thông tin chi nhánh",
        description: "Không xác định được chi nhánh hiện tại để tạo đơn hàng.",
        variant: "destructive",
      });
      return;
    }

    setProcessing(true);
    try {
      const orderData = {
        subTotal,
        taxAmount,
        discountAmount,
        couponCode: couponCode || null,
        totalAmount: total,
        branchId: resolvedBranchId,
        cashierId: userProfile?.id,
        customerId: selectedCustomer?.id || null,
        customer: selectedCustomer || null,
        items: cart.map((item) => ({
          productId: item.id,
          quantity: item.quantity,
          price:
            (item.sellingPrice ?? item.price ?? 0) * Number(item.quantity || 1),
        })),
        paymentType: normalizedSelected,
        note,
      };

      const createdOrder = await dispatch(createOrder(orderData)).unwrap();
      // Reset giỏ hàng trước, sau đó gán currentOrder để InvoiceDialog hiển thị hóa đơn
      dispatch(resetOrder());
      dispatch(setCurrentOrder(createdOrder));
      dispatch(getInventoryByBranch(resolvedBranchId));
      setCashReceived("");
      setShowPaymentDialog(false);
      setShowReceiptDialog(true);

      const newCustomerPts = createdOrder?.customer?.loyaltyPoints;
      toast({
        title: "Order Completed",
        description:
          newCustomerPts !== undefined && newCustomerPts !== null
            ? `Đơn #${createdOrder.id} hoàn tất! Điểm tích lũy hiện tại của KH: ${newCustomerPts} điểm.`
            : `Order #${createdOrder.id} created successfully`,
      });
    } catch (error) {
      toast({
        title: "Failed to create order",
        description:
          typeof error === "string"
            ? error
            : error?.message || "Failed to create order",
        variant: "destructive",
      });
    } finally {
      setProcessing(false);
    }
  };

  return (
    <Dialog open={showPaymentDialog} onOpenChange={setShowPaymentDialog}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-xl font-bold">
            Xác nhận Thanh toán
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Tóm tắt số tiền thanh toán */}
          <div className="rounded-lg bg-muted/60 p-3.5 space-y-2 border text-sm">
            <div className="flex justify-between text-muted-foreground">
              <span>Tạm tính ({cart.length} mặt hàng):</span>
              <span>₫{Number(subTotal || 0).toLocaleString("vi-VN")}</span>
            </div>
            <div className="flex justify-between text-muted-foreground">
              <span>Thuế VAT (10%):</span>
              <span>₫{Number(taxAmount || 0).toLocaleString("vi-VN")}</span>
            </div>
            {discountAmount > 0 && (
              <div className="flex justify-between text-red-600 font-medium">
                <span>
                  Giảm giá{" "}
                  {isRedeemingPoints
                    ? `(Đổi ${pointsUsed} điểm)`
                    : couponCode
                      ? `(${couponCode})`
                      : ""}
                  :
                </span>
                <span>
                  -₫{Number(discountAmount || 0).toLocaleString("vi-VN")}
                </span>
              </div>
            )}
            <Separator />
            <div className="flex justify-between items-center text-base font-bold pt-0.5">
              <span>Tổng thanh toán:</span>
              <span className="text-xl text-emerald-600">
                ₫{Number(total || 0).toLocaleString("vi-VN")}
              </span>
            </div>
          </div>

          {/* Thông tin tích điểm & đổi điểm của Khách hàng */}
          {selectedCustomer && (
            <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 space-y-1.5 text-xs">
              <div className="flex items-center justify-between">
                <span className="font-semibold flex items-center gap-1.5 text-foreground">
                  <User className="w-3.5 h-3.5 text-emerald-600" />
                  {selectedCustomer.fullName || selectedCustomer.name}
                </span>
                <Badge
                  variant="outline"
                  className="bg-amber-500/15 text-amber-700 dark:text-amber-300 border-amber-500/30 flex items-center gap-1"
                >
                  <Star className="w-3 h-3 fill-amber-500 text-amber-500" />
                  Hiện có: {availablePoints} điểm
                </Badge>
              </div>

              {pointsUsed > 0 && (
                <div className="flex justify-between text-red-600 font-medium">
                  <span className="flex items-center gap-1">
                    <Gift className="w-3 h-3" />
                    Điểm sử dụng đơn này:
                  </span>
                  <span>
                    -{pointsUsed} điểm (-
                    {(pointsUsed * VND_PER_POINT_REDEEMED).toLocaleString(
                      "vi-VN",
                    )}
                    ₫)
                  </span>
                </div>
              )}

              <div className="flex justify-between text-emerald-600 dark:text-emerald-400 font-medium">
                <span className="flex items-center gap-1">
                  <Sparkles className="w-3 h-3" />
                  Điểm tích lũy thêm:
                </span>
                <span>+{earnedPoints} điểm</span>
              </div>

              <div className="flex justify-between pt-1 border-t border-amber-500/20 font-semibold text-foreground">
                <span>Số dư điểm sau thanh toán:</span>
                <span className="text-amber-600 dark:text-amber-400">
                  {projectedPoints} điểm
                </span>
              </div>
            </div>
          )}

          {/* Chọn phương thức thanh toán */}
          <div className="space-y-2">
            <label className="text-sm font-medium block">
              Phương thức thanh toán
            </label>
            <div className="grid grid-cols-1 gap-2">
              {PAYMENT_METHODS.map((method) => {
                const Icon = method.icon;
                const isSelected = normalizedSelected === method.id;
                return (
                  <button
                    key={method.id}
                    type="button"
                    onClick={() => handleSelectPaymentMethod(method.id)}
                    className={`flex items-center justify-between p-3 rounded-lg border text-sm font-medium transition-all ${
                      isSelected
                        ? `${method.activeClass} ring-1 ring-current`
                        : "hover:bg-muted/60 border-border"
                    }`}
                  >
                    <span className="flex items-center gap-2.5">
                      <Icon className="w-4 h-4" />
                      {method.label}
                    </span>
                    {isSelected && <CheckCircle2 className="w-4 h-4" />}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Tính tiền thừa khi chọn Tiền mặt (CASH) */}
          {normalizedSelected === "CASH" && (
            <div className="space-y-2 pt-1">
              <label className="text-xs font-medium text-muted-foreground block">
                Tiền khách đưa (Tùy chọn - để tính tiền thừa)
              </label>
              <Input
                type="number"
                min="0"
                placeholder={`Nhập số tiền khách đưa (≥ ${Number(total || 0).toLocaleString("vi-VN")}₫)`}
                value={cashReceived}
                onChange={(e) => setCashReceived(e.target.value)}
              />
              {numericCashReceived >= total && total > 0 && (
                <div className="flex justify-between items-center text-sm font-semibold text-emerald-600 bg-emerald-500/10 px-3 py-2 rounded-md">
                  <span>Tiền thừa trả khách:</span>
                  <span>₫{changeAmount.toLocaleString("vi-VN")}</span>
                </div>
              )}
            </div>
          )}
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button
            type="button"
            variant="outline"
            onClick={() => setShowPaymentDialog(false)}
            disabled={processing}
          >
            Hủy
          </Button>
          <Button
            type="button"
            onClick={handlePayment}
            disabled={processing || cart.length === 0}
            className="bg-emerald-600 hover:bg-emerald-700 text-white"
          >
            {processing ? (
              <>
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                Đang xử lý...
              </>
            ) : (
              `Thanh toán ₫${Number(total || 0).toLocaleString("vi-VN")}`
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PaymentDialog;
