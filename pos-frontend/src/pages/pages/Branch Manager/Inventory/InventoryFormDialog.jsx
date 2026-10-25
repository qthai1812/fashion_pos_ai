import React from "react";
import { useSelector } from "react-redux";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Filter } from "lucide-react";

const InventoryFormDialog = ({
  open,
  onOpenChange,
  selectedProductId,
  setSelectedProductId,
  quantity,
  setQuantity,
  onSubmit,
  mode = "add",
}) => {
  const products = useSelector((state) => state.product.products);
  const isEdit = mode === "edit";

  const selectedProduct = products.find(
    (p) => String(p.id) === String(selectedProductId)
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {isEdit ? "Edit Inventory" : "Add Inventory"}
          </DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Update the quantity for this product."
              : "Select a product and quantity to add. If the product already has inventory, the quantity will be added to the existing amount."}
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          {/* Product */}
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor="product" className="text-right">
              Product
            </Label>
            {isEdit ? (
              <div className="col-span-3 space-y-0.5">
                <Input
                  id="product"
                  value={selectedProduct?.name || ""}
                  disabled
                />
                <p className="text-xs text-muted-foreground pl-1">
                  SKU: {selectedProduct?.sku || "—"} · ID: {selectedProductId}
                </p>
              </div>
            ) : (
              <Select
                value={String(selectedProductId || "")}
                onValueChange={(value) => setSelectedProductId(value)}
              >
                <SelectTrigger
                  startIcon={<Filter className="h-4 w-4 text-gray-500" />}
                  className="col-span-3 w-full"
                >
                  <SelectValue placeholder="Select a Product" />
                </SelectTrigger>
                <SelectContent>
                  {products.map((product) => (
                    <SelectItem key={product.id} value={String(product.id)}>
                      <span>{product.name}</span>
                      <span className="ml-2 text-xs text-muted-foreground font-mono">
                        #{product.sku}
                      </span>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </div>

          {/* Quantity */}
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor="quantity" className="text-right">
              Quantity
            </Label>
            <div className="col-span-3">
              <Input
                id="quantity"
                type="number"
                min={1}
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
              />
              {!isEdit && (
                <p className="text-xs text-muted-foreground mt-1 pl-1">
                  Will be added to existing stock if product already exists.
                </p>
              )}
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            onClick={onSubmit}
            disabled={!selectedProductId || !quantity || Number(quantity) < 1}
          >
            {isEdit ? "Update Inventory" : "Add Inventory"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default InventoryFormDialog;
