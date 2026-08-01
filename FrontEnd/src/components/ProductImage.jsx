import { useEffect, useState } from "react";
import API from "../axios";
import unplugged from "../assets/unplugged.png";

const toDataUrl = (base64String, mimeType = "image/jpeg") => {
  if (!base64String) {
    return "";
  }

  if (base64String.startsWith("data:")) {
    return base64String;
  }

  if (base64String.startsWith("http")) {
    return base64String;
  }

  return `data:${mimeType};base64,${base64String}`;
};

const ProductImage = ({ productId, base64Data = "", alt = "Product image", className, style, fallbackSrc = unplugged, ...imgProps }) => {
  const [src, setSrc] = useState(base64Data ? toDataUrl(base64Data) : fallbackSrc);

  useEffect(() => {
    let objectUrl = "";
    let isActive = true;

    const loadImage = async () => {
      if (!productId) {
        setSrc(base64Data ? toDataUrl(base64Data) : fallbackSrc);
        return;
      }

      try {
        const response = await API.get(`/api/product/${productId}/image`, {
          responseType: "blob",
        });

        if (!isActive) {
          return;
        }

        objectUrl = URL.createObjectURL(response.data);
        setSrc(objectUrl);
      } catch {
        if (!isActive) {
          return;
        }

        setSrc(base64Data ? toDataUrl(base64Data) : fallbackSrc);
      }
    };

    loadImage();

    return () => {
      isActive = false;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [productId, base64Data, fallbackSrc]);

  return <img src={src} alt={alt} className={className} style={style} {...imgProps} />;
};

export default ProductImage;