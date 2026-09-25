import {
  AbsoluteFill,
  CanvasImage,
  Easing,
  Interactive,
  interpolate,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";

type CardRevealProps = {
  asset: string;
  cardNumber: string;
  cardTitle: string;
  rarity: string;
  accent: string;
  backgroundStart: string;
  backgroundEnd: string;
};

export const CardReveal: React.FC<CardRevealProps> = ({
  asset,
  cardNumber,
  cardTitle,
  rarity,
  accent,
  backgroundStart,
  backgroundEnd,
}) => {
  const frame = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();

  return (
    <AbsoluteFill
      style={{
        overflow: "hidden",
        background: `linear-gradient(160deg, ${backgroundStart} 0%, ${backgroundEnd} 100%)`,
        color: "#fffaf0",
        fontFamily: "Arial, Helvetica, sans-serif",
      }}
    >
      <Interactive.Div
        name="Ambient glow"
        style={{
          position: "absolute",
          width: 900,
          height: 900,
          left: 90,
          top: 230,
          borderRadius: 999,
          background: accent,
          filter: "blur(180px)",
          opacity: interpolate(frame, [0, 18, durationInFrames - 12], [0, 0.28, 0.18], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      />
      <Interactive.Div
        name="Card counter"
        style={{
          position: "absolute",
          top: 118,
          left: 90,
          right: 90,
          textAlign: "center",
          color: "#fff2cf",
          fontSize: 42,
          fontWeight: 800,
          letterSpacing: 8,
          opacity: interpolate(frame, [0, 10], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        {cardNumber}
      </Interactive.Div>
      <CanvasImage
        name={`${cardTitle} card`}
        src={staticFile(`cards/${asset}`)}
        width={760}
        height={1140}
        fit="contain"
        style={{
          position: "absolute",
          top: 230,
          left: 160,
          borderRadius: 44,
          filter: "drop-shadow(0 40px 42px rgba(0, 0, 0, 0.42))",
          opacity: interpolate(frame, [0, 10, durationInFrames - 10, durationInFrames], [0, 1, 1, 0.92], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          scale: interpolate(frame, [0, 18, durationInFrames], [0.84, 1, 1.035], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 200 }),
            output: "perceptual-scale",
          }),
          rotate: interpolate(frame, [0, 18], ["-4deg", "0deg"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 200 }),
          }),
        }}
      />
      <Interactive.Div
        name="Card title"
        style={{
          position: "absolute",
          top: 1435,
          left: 80,
          right: 80,
          textAlign: "center",
          color: "#fffaf0",
          fontSize: 96,
          lineHeight: 1.02,
          fontWeight: 900,
          letterSpacing: -3,
          textShadow: "0 8px 28px rgba(0, 0, 0, 0.45)",
          opacity: interpolate(frame, [12, 24], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
          translate: interpolate(frame, [12, 24], ["0px 36px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        {cardTitle}
      </Interactive.Div>
      <Interactive.Div
        name="Rarity badge"
        style={{
          position: "absolute",
          top: 1575,
          left: 270,
          right: 270,
          minHeight: 92,
          borderRadius: 999,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          border: "3px solid rgba(255, 255, 255, 0.56)",
          background: accent,
          color: "#1d1424",
          fontSize: 48,
          fontWeight: 900,
          letterSpacing: 4,
          boxShadow: "0 16px 40px rgba(0, 0, 0, 0.28)",
          opacity: interpolate(frame, [20, 32], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          scale: interpolate(frame, [20, 32], [0.78, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 180 }),
            output: "perceptual-scale",
          }),
        }}
      >
        {rarity}
      </Interactive.Div>
      <Interactive.Div
        name="Pack note"
        style={{
          position: "absolute",
          top: 1730,
          left: 100,
          right: 100,
          textAlign: "center",
          color: "rgba(255, 250, 240, 0.82)",
          fontSize: 44,
          lineHeight: 1.2,
          fontWeight: 700,
          opacity: interpolate(frame, [28, 40], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        One Fluffy Pack • Three cards
      </Interactive.Div>
    </AbsoluteFill>
  );
};
