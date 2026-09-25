import {
  AbsoluteFill,
  Audio,
  Img,
  interpolate,
  Sequence,
  staticFile,
  useCurrentFrame,
} from "remotion";
import { CardReveal } from "./scenes/CardReveal";

const CozyHook: React.FC = () => {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill style={{background: "linear-gradient(160deg, #3b2440, #17283b)", color: "#fff8e9", fontFamily: "Arial, sans-serif", textAlign: "center"}}>
      <Img src={staticFile("cards/fireplace-friend.png")} style={{position: "absolute", width: 690, height: 1035, objectFit: "contain", left: 195, top: 170, opacity: 0.42, filter: "blur(12px)", transform: `scale(${interpolate(frame, [0, 90], [1, 1.13])})`}} />
      <div style={{position: "absolute", top: 180, left: 70, right: 70, color: "#ffd789", fontSize: 46, fontWeight: 900, letterSpacing: 9}}>PURRRFOLIO</div>
      <div style={{position: "absolute", top: 750, left: 70, right: 70, fontSize: 112, lineHeight: 1.02, fontWeight: 900, textShadow: "0 12px 36px #20132d", opacity: interpolate(frame, [6, 24], [0, 1], {extrapolateRight: "clamp"})}}>Meet the Cozy Home cats</div>
      <div style={{position: "absolute", top: 1190, left: 110, right: 110, fontSize: 51, lineHeight: 1.2, fontWeight: 700}}>Three little stories, one cat card collection.</div>
      <div style={{position: "absolute", top: 1590, left: 230, right: 230, padding: 25, borderRadius: 999, background: "#ffd789", color: "#21172a", fontSize: 44, fontWeight: 900}}>LET’S LOOK 🐾</div>
    </AbsoluteFill>
  );
};

const CozyCta: React.FC = () => (
  <AbsoluteFill style={{background: "linear-gradient(160deg, #2c1b41, #142c34)", color: "#fff8e9", fontFamily: "Arial, sans-serif", textAlign: "center"}}>
    <div style={{position: "absolute", top: 155, left: 80, right: 80, display: "flex", justifyContent: "center", alignItems: "center", gap: 15}}>
      {["sock-thief", "fireplace-friend", "pillow-fort"].map((card, index) => <Img key={card} src={staticFile(`cards/${card}.png`)} style={{width: 270, height: 405, objectFit: "contain", transform: `rotate(${(index - 1) * 9}deg)`, filter: "drop-shadow(0 20px 18px #101020)"}} />)}
    </div>
    <div style={{position: "absolute", top: 740, left: 80, right: 80, fontSize: 102, lineHeight: 1, fontWeight: 900}}>Build your cat card collection</div>
    <div style={{position: "absolute", top: 1110, left: 110, right: 110, fontSize: 53, lineHeight: 1.2, fontWeight: 700}}>Collect cozy cats in Telegram.</div>
    <div style={{position: "absolute", top: 1430, left: 110, right: 110, padding: 35, borderRadius: 999, background: "#ffd789", color: "#24152f", fontSize: 66, fontWeight: 900}}>@PurrrfolioBot</div>
    <div style={{position: "absolute", top: 1720, left: 100, right: 100, fontSize: 42, fontWeight: 700, opacity: 0.8}}>Open • Collect • Share</div>
  </AbsoluteFill>
);

export const CozyHomeFridayVideo: React.FC = () => {
  return (
    <AbsoluteFill>
      <Audio src={staticFile("audio/generated/youtube-short-20260925-friday-01.wav")} volume={(frame) => 0.32 * Math.min(1, frame / 15, (450 - frame) / 18)} />
      <Sequence durationInFrames={90}><CozyHook /></Sequence>
      <Sequence from={90} durationInFrames={90}><CardReveal asset="sock-thief.png" cardNumber="COZY HOME 1 OF 3" cardTitle="Sock Thief" rarity="COMMON" accent="#f4d89a" backgroundStart="#3c2846" backgroundEnd="#182c3c" note="A cat with a tiny secret" /></Sequence>
      <Sequence from={180} durationInFrames={90}><CardReveal asset="fireplace-friend.png" cardNumber="COZY HOME 2 OF 3" cardTitle="Fireplace Friend" rarity="RARE" accent="#f3b477" backgroundStart="#4b2634" backgroundEnd="#1d2940" note="Warmth for a rainy day" /></Sequence>
      <Sequence from={270} durationInFrames={90}><CardReveal asset="pillow-fort.png" cardNumber="COZY HOME 3 OF 3" cardTitle="Pillow Fort" rarity="RARE" accent="#b5d6f2" backgroundStart="#293a50" backgroundEnd="#2c2542" note="The coziest hideaway" /></Sequence>
      <Sequence from={360} durationInFrames={90}><CozyCta /></Sequence>
    </AbsoluteFill>
  );
};
