package common.models.item;

public class Art extends Item{
    private String artist;

    public Art(String id, String name, String description, double startingPrice, String sellerId, String artist){
        super(id, name, description, startingPrice, sellerId);
        this.artist = artist;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    @Override
    public String getInfo(){
        return "Tên sản phẩm: " +name +"| ID người bán: " + sellerId  + "\nMô tả: "+ description+ "\nGiá khởi điểm: " +startingPrice + "\nTên nghệ sĩ: " + artist;
    }

}
