export const uploadFileToServer = async (file) => {
  if (!file) return;
  
  const data = new FormData();
  data.append("file", file);

  const token = localStorage.getItem("jwt");

  try {
    const res = await fetch(`http://localhost:5000/api/media/upload`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`
      },
      body: data,
    });
    
    if (!res.ok) {
      throw new Error("Upload failed");
    }
    
    const fileData = await res.json();
    console.log("url : ", fileData);
    return fileData.url;
  } catch (error) {
    console.error("error uploading file", error);
  }
};
