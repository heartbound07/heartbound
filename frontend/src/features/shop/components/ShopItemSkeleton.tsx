const ShopItemSkeleton = () => {
    return (
      <div className="shop-item-card">
        <div className="shop-item-image skeleton"></div>
        <div className="p-4 space-y-2">
          <div className="h-6 w-2/3 skeleton rounded"></div>
          <div className="h-4 w-full skeleton rounded"></div>
          <div className="h-8 w-full skeleton rounded mt-4"></div>
        </div>
      </div>
    );
  };
  
  export default ShopItemSkeleton; 