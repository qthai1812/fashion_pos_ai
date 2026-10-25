import React from "react";
import { useDispatch, useSelector } from "react-redux";
import {
  selectDiscount,
  setDiscount,
} from "../../../Redux Toolkit/features/cart/cartSlice";
import { Tag } from "lucide-react";
import { Button } from "../../../components/ui/button";
import { Input } from "@/components/ui/input";

const DiscountSection = () => {
  const dispatch = useDispatch();
  const discount = useSelector(selectDiscount);

  const handleSetDiscount = (e) => {
    dispatch(
      setDiscount({ ...discount, value: parseFloat(e.target.value) || 0 })
    );
  };

  return (
    <div className="p-4 border-b">
      <h2 className="text-lg font-semibold mb-3 flex items-center">
        <Tag className="w-5 h-5 mr-2" />
        Discount
      </h2>
      <div className="space-y-3">
        <Input
          type="number"
          min="0"
          placeholder="Discount amount"
          value={discount.value || ""}
          onChange={handleSetDiscount}
        />
        <div className="flex space-x-2">
          <Button
            type="button"
            variant={discount.type === "percentage" ? "default" : "outline"}
            size="sm"
            className="flex-1"
            onClick={() =>
              dispatch(setDiscount({ ...discount, type: "percentage" }))
            }
          >
            %
          </Button>
          <Button
            type="button"
            variant={discount.type === "fixed" ? "default" : "outline"}
            size="sm"
            className="flex-1"
            onClick={() =>
              dispatch(setDiscount({ ...discount, type: "fixed" }))
            }
          >
            ₹
          </Button>
        </div>
      </div>
    </div>
  );
};

export default DiscountSection;
