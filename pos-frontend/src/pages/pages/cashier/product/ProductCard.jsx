import React from "react";
import { Card, CardContent } from "../../../components/ui/card";
import { Badge } from "../../../components/ui/badge";
import { useDispatch } from "react-redux";
import { useToast } from "../../../components/ui/use-toast";
import { addToCart } from "../../../Redux Toolkit/features/cart/cartSlice";

const ProductCard = ({ product }) => {
  const dispatch = useDispatch();
  const { toast } = useToast();

  const handleAddToCart = (item) => {
    dispatch(addToCart(item));
    toast({
      title: "Added to cart",
      description: `${item.name} added to cart`,
      duration: 1500,
    });
  };

  return (
    <Card
      key={product.id}
      className="cursor-pointer hover:shadow-md transition-all duration-200 border-2 hover:border-primary"
      onClick={() => handleAddToCart(product)}
    >
      <CardContent className="p-3">
        <div className="aspect-square bg-muted rounded-md mb-2 flex items-center justify-center overflow-hidden">
          {product.image ? (
            <img
              className="h-full w-full object-cover"
              src={product.image}
              alt={product.name}
            />
          ) : (
            <span className="text-xs text-muted-foreground">No Image</span>
          )}
        </div>
        <h3 className="font-medium text-sm truncate" title={product.name}>
          {product.name}
        </h3>
        <p className="text-xs text-muted-foreground truncate">{product.sku}</p>
        <div className="flex items-center justify-between mt-2 gap-1">
          <span className="font-bold text-emerald-600 text-sm">
            ₹{Number(product.sellingPrice ?? product.price ?? 0).toLocaleString()}
          </span>
          <Badge variant="secondary" className="text-xs truncate max-w-[100px]">
            {typeof product.category === "object"
              ? product.category?.name
              : product.category || "General"}
          </Badge>
        </div>
      </CardContent>
    </Card>
  );
};

export default ProductCard;
