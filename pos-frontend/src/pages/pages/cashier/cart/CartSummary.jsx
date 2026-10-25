import React, { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Separator } from "../../../components/ui/separator";
import { Input } from "../../../components/ui/input";
import { Button } from "../../../components/ui/button";
import { useToast } from "../../../components/ui/use-toast";
import {
  selectDiscountAmount,
  selectSubtotal,
  selectTax,
  selectTotal,
  selectCouponCode,
  setDiscount,
  setCouponCode,
} from "../../../Redux Toolkit/features/cart/cartSlice";
import { applyCoupon } from "../../../Redux Toolkit/features/coupon/couponThunks";
import { X } from "lucide-react";

const CartSummary = () => {
  const dispatch = useDispatch();
  const { toast } = useToast();
  
  const subtotal = useSelector(selectSubtotal);
  const tax = useSelector(selectTax);
  const discountAmount = useSelector(selectDiscountAmount);
  const total = useSelector(selectTotal);
  const appliedCouponCode = useSelector(selectCouponCode);
  
  const { branch } = useSelector((state) => state.branch);
  
  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(false);

  const handleApplyCoupon = async () => {
    if (!code.trim()) return;
    
    const storeId = branch?.storeId;
    if (!storeId) {
      toast({ title: "Error", description: "Store ID not found.", variant: "destructive" });
      return;
    }

    setLoading(true);
    try {
      const coupon = await dispatch(applyCoupon({ code: code.trim(), storeId })).unwrap();
      
      if (coupon.minOrderValue && subtotal < coupon.minOrderValue) {
        toast({ 
          title: "Minimum Order Not Met", 
          description: `This coupon requires a minimum order of ₹${coupon.minOrderValue}`,
          variant: "destructive" 
        });
        return;
      }
      
      dispatch(setDiscount({ type: coupon.discountType.toLowerCase(), value: coupon.discountValue }));
      dispatch(setCouponCode(coupon.code));
      setCode("");
      toast({ title: "Success", description: "Coupon applied successfully!" });
    } catch (err) {
      toast({ title: "Invalid Coupon", description: err || "Failed to apply coupon", variant: "destructive" });
    } finally {
      setLoading(false);
    }
  };

  const handleRemoveCoupon = () => {
    dispatch(setDiscount({ type: "percentage", value: 0 }));
    dispatch(setCouponCode(null));
  };

  return (
    <div className="border-t bg-muted p-4">
      <div className="space-y-4 text-sm">
        
        {/* Coupon Section */}
        <div className="flex gap-2">
          <Input 
            placeholder="Enter coupon code" 
            value={code} 
            onChange={(e) => setCode(e.target.value)}
            disabled={loading || appliedCouponCode}
          />
          <Button 
            variant={appliedCouponCode ? "destructive" : "secondary"}
            onClick={appliedCouponCode ? handleRemoveCoupon : handleApplyCoupon}
            disabled={loading || (!code.trim() && !appliedCouponCode)}
          >
            {appliedCouponCode ? <X className="w-4 h-4" /> : "Apply"}
          </Button>
        </div>
        
        {appliedCouponCode && (
          <div className="text-xs text-emerald-600 font-medium">
            Coupon '{appliedCouponCode}' applied!
          </div>
        )}

        <div className="space-y-2">
          <div className="flex justify-between">
            <span>Subtotal:</span>
            <span>₹{subtotal.toFixed(2)}</span>
          </div>
          <div className="flex justify-between">
            <span>Tax (18% GST):</span>
            <span>₹{tax.toFixed(2)}</span>
          </div>
          <div className="flex justify-between">
            <span>Discount:</span>
            <span className="text-red-600">- ₹{discountAmount.toFixed(2)}</span>
          </div>
          <Separator />
          <div className="flex justify-between text-lg font-bold">
            <span>Total:</span>
            <span className="text-green-600">₹{total?.toFixed(2)}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CartSummary;
