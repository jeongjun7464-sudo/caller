from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

SUIT_SYMBOLS = {"SPADES": "♠", "HEARTS": "♥", "CLUBS": "♣", "DIAMONDS": "♦"}
RANK_LABELS = {"ACE":"A","TWO":"2","THREE":"3","FOUR":"4","FIVE":"5","SIX":"6","SEVEN":"7","EIGHT":"8","NINE":"9","TEN":"10","JACK":"J","QUEEN":"Q","KING":"K"}

def _font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [Path("C:/Windows/Fonts/seguisym.ttf"), Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")]
    for path in candidates:
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()

def create_card_image(card_id: str, destination: Path, height: int = 1920) -> Path:
    suit, rank = card_id.split("_", 1)
    image = Image.new("RGB", (1080, height), "#07111F")
    draw = ImageDraw.Draw(image)
    top, bottom = int(height * .135), int(height * .865)
    draw.rounded_rectangle((130, top, 950, bottom), radius=52, fill="white")
    red = suit in {"HEARTS", "DIAMONDS"}
    color = "#D91E36" if red else "#101820"
    symbol, label = SUIT_SYMBOLS[suit], RANK_LABELS[rank]
    corner_font, symbol_font = _font(120), _font(430)
    draw.text((190, top + 60), f"{label}\n{symbol}", font=corner_font, fill=color, spacing=0)
    bbox = draw.textbbox((0, 0), symbol, font=symbol_font)
    draw.text(((1080-(bbox[2]-bbox[0]))/2, height/2-(bbox[3]-bbox[1])/2), symbol, font=symbol_font, fill=color)
    draw.text((540, height * .91), "CARD CALLER", font=_font(44), fill="#9FB0C5", anchor="mm")
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination, format="JPEG", quality=94, optimize=True)
    return destination
