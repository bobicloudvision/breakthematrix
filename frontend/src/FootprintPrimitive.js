/**
 * FootprintPrimitive - Displays order flow data (buy/sell volume) within each candle
 * Shows volume traded at each price level with color-coding for buy/sell pressure
 * 
 * Data format expected:
 * {
 *   time: number (unix timestamp),
 *   volumeByPrice: [
 *     { price: number, buyVolume: number, sellVolume: number }
 *   ]
 * }
 */

class FootprintPaneView {
    constructor(source) {
        this._source = source;
    }

    update() {
        // Called when data changes
    }

    renderer() {
        return this._source._renderer;
    }
}

class FootprintRenderer {
    constructor(source) {
        this._source = source;
    }

    draw(target) {
        target.useBitmapCoordinateSpace(scope => this._drawImpl(scope, target));
    }

    _drawImpl(scope, target) {
        const ctx = scope.context;

        const data = this._source._data;

        if (!data || data.length === 0) {
            return;
        }

        const chart = this._source._chart;
        const series = this._source._series;
        
        if (!chart || !series) {
            return;
        }

        let drawnCount = 0;
        let skippedCount = 0;

        ctx.save();
        
        // Scale font size for bitmap coordinates
        const pixelRatio = scope.horizontalPixelRatio || 1;
        const fontSize = this._fontSize * pixelRatio;
        ctx.font = `${fontSize}px monospace`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';

        // Get visible time range
        const timeScale = chart.timeScale();
        
        if (!timeScale || !series) {
            ctx.restore();
            return;
        }

        const visibleRange = timeScale.getVisibleLogicalRange();
        
        if (!visibleRange) {
            ctx.restore();
            return;
        }

        // Calculate max volume for scaling volume bars
        let maxVolume = 0;
        data.forEach(candle => {
            if (candle.volume && candle.volume > maxVolume) {
                maxVolume = candle.volume;
            }
        });

        // Draw footprint data for each candle
        data.forEach((candleData, candleIndex) => {
            // Get candle X position
            const x = timeScale.timeToCoordinate(candleData.time);
            if (x === null || x === undefined) {
                skippedCount++;
                return;
            }
            
            // Scale X coordinate for bitmap
            const scaledX = x * pixelRatio;

            // Get bar spacing to determine available width
            const barSpacing = timeScale.options().barSpacing || 6;
            const candleWidth = barSpacing * 0.95 * pixelRatio; // Increased from 0.8 to 0.95 for wider candles

            // Get candle OHLC coordinates for positioning side boxes
            let scaledOpenY, scaledHighY, scaledLowY, scaledCloseY;
            
            if (candleData.open && candleData.high && candleData.low && candleData.close) {
                const openY = series.priceToCoordinate(candleData.open);
                const highY = series.priceToCoordinate(candleData.high);
                const lowY = series.priceToCoordinate(candleData.low);
                const closeY = series.priceToCoordinate(candleData.close);
                
                if (openY !== null && highY !== null && lowY !== null && closeY !== null) {
                    scaledOpenY = openY * pixelRatio;
                    scaledHighY = highY * pixelRatio;
                    scaledLowY = lowY * pixelRatio;
                    scaledCloseY = closeY * pixelRatio;
                } else {
                    return; // Skip if can't get coordinates
                }
            } else {
                return; // Skip if no OHLC data
            }

            // Calculate total buy and sell volumes AND collect price levels
            let totalBuyVolume = 0;
            let totalSellVolume = 0;
            let pocPrice = null;
            let maxVolume = 0;
            const priceLevels = [];
            
            // Collect all price levels
            if (candleData.volumeByPrice && candleData.volumeByPrice.length > 0) {
                candleData.volumeByPrice.forEach(level => {
                    const { price, buyVolume, sellVolume } = level;
                    
                    totalBuyVolume += buyVolume || 0;
                    totalSellVolume += sellVolume || 0;
                    
                    const levelTotal = (buyVolume || 0) + (sellVolume || 0);
                    if (levelTotal > maxVolume) {
                        maxVolume = levelTotal;
                        pocPrice = level.price;
                    }
                    
                    // Skip if volume too small
                    if (levelTotal < this._source._minVolumeToShow) {
                        return;
                    }
                    
                    // Skip if both volumes are effectively zero (ignore 0.00 values)
                    if (buyVolume < 0.001 && sellVolume < 0.001) {
                        return;
                    }
                    
                    priceLevels.push({ price, buyVolume, sellVolume });
                });
            }
            
            const delta = totalBuyVolume - totalSellVolume;
            
            // Draw footprint data INSIDE candle - GRID LAYOUT
            if (priceLevels.length > 0) {
                // Filter to show only the most important price levels
                const levelsWithScore = priceLevels.map(level => {
                    const totalVolume = level.buyVolume + level.sellVolume;
                    const delta = Math.abs(level.buyVolume - level.sellVolume);
                    const imbalance = totalVolume > 0 ? delta / totalVolume : 0;
                    const score = totalVolume * (1 + imbalance * 2);
                    return { ...level, score, totalVolume };
                });
                
                levelsWithScore.sort((a, b) => b.score - a.score);
                const maxLevels = barSpacing > 40 ? 10 : (barSpacing > 25 ? 7 : 5);
                const importantLevels = levelsWithScore.slice(0, Math.min(maxLevels, levelsWithScore.length));
                importantLevels.sort((a, b) => b.price - a.price);
                const levelsToDisplay = importantLevels;
                
                // Calculate max individual volume for big order highlighting
                const maxIndividualVolume = Math.max(
                    ...levelsToDisplay.map(l => Math.max(l.buyVolume, l.sellVolume))
                );
                const bigOrderThreshold = maxIndividualVolume * 0.5;
                
                // Calculate grid dimensions
                const candleBodyTop = Math.min(scaledOpenY, scaledCloseY);
                const candleBodyBottom = Math.max(scaledOpenY, scaledCloseY);
                const candleBodyHeight = Math.abs(candleBodyBottom - candleBodyTop);
                const minCandleHeight = 100 * pixelRatio; // Increased for larger text
                const effectiveCandleHeight = Math.max(candleBodyHeight, minCandleHeight);
                
                const rowHeight = effectiveCandleHeight / levelsToDisplay.length;
                const candleLeft = scaledX - candleWidth / 2;
                const candleRight = scaledX + candleWidth / 2;
                
                const startY = candleBodyTop - (effectiveCandleHeight > candleBodyHeight ? (effectiveCandleHeight - candleBodyHeight) / 2 : 0);
                
                // Draw grid structure
                levelsToDisplay.forEach((level, index) => {
                    const cellTop = startY + (index * rowHeight);
                    const cellBottom = cellTop + rowHeight;
                    const cellCenterY = cellTop + (rowHeight / 2);
                    
                    // Calculate values
                    const buyVol = level.buyVolume || 0;
                    const sellVol = level.sellVolume || 0;
                    const totalVolume = buyVol + sellVol;
                    const imbalance = buyVol - sellVol;
                    const absImbalance = Math.abs(imbalance);
                    
                    // Skip if no significant volume
                    if (totalVolume < 0.001) {
                        return;
                    }
                    
                    // Calculate imbalance ratio (0-1)
                    const imbalanceRatio = totalVolume > 0 ? absImbalance / totalVolume : 0;
                    
                    // Identify big imbalances (>50% and high volume)
                    const isBigImbalance = absImbalance >= bigOrderThreshold && imbalanceRatio > 0.3;
                    
                    // Determine cell color based on imbalance direction and strength
                    let bgColor, textColor;
                    if (isBigImbalance) {
                        // Big imbalance - bright highlight
                        if (imbalance > 0) {
                            bgColor = 'rgba(74, 222, 255, 0.9)'; // Bright cyan for big buy imbalance
                            textColor = 'rgb(255, 255, 255)'; // White text
                        } else {
                            bgColor = 'rgba(255, 237, 74, 0.9)'; // Bright yellow for big sell imbalance
                            textColor = 'rgb(0, 0, 0)'; // Black text on yellow
                        }
                    } else {
                        // Normal - gradient based on strength
                        if (imbalance > 0) {
                            const alpha = 0.15 + (imbalanceRatio * 0.6); // 0.15 to 0.75
                            bgColor = `rgba(16, 185, 129, ${alpha})`; // Green for buy imbalance
                            textColor = alpha > 0.5 ? 'rgb(255, 255, 255)' : 'rgba(16, 185, 129, 1)'; // White on dark green
                        } else {
                            const alpha = 0.15 + (imbalanceRatio * 0.6); // 0.15 to 0.75
                            bgColor = `rgba(239, 68, 68, ${alpha})`; // Red for sell imbalance
                            textColor = 'rgb(255, 255, 255)'; // White text on red backgrounds
                        }
                    }
                    
                    // Draw full-width cell background
                    ctx.fillStyle = bgColor;
                    ctx.fillRect(candleLeft, cellTop, candleWidth, rowHeight);
                    
                    // Draw horizontal grid line (between rows)
                    if (index > 0) {
                        ctx.strokeStyle = 'rgba(100, 100, 100, 0.3)';
                        ctx.lineWidth = 1 * pixelRatio;
                        ctx.beginPath();
                        ctx.moveTo(candleLeft, cellTop);
                        ctx.lineTo(candleRight, cellTop);
                        ctx.stroke();
                    }
                    
                    // Format text: "BuyVolxSellVol" (e.g., "123x128")
                    let volumeText;
                    if (buyVol >= 1 || sellVol >= 1) {
                        volumeText = `${buyVol.toFixed(0)}x${sellVol.toFixed(0)}`;
                    } else {
                        volumeText = `${buyVol.toFixed(2)}x${sellVol.toFixed(2)}`;
                    }
                    
                    // Draw volume text (centered in full cell)
                    ctx.font = `${isBigImbalance ? 'bold' : ''} ${fontSize}px -apple-system, BlinkMacSystemFont, monospace`;
                    ctx.textBaseline = 'middle';
                    ctx.textAlign = 'center';
                    ctx.fillStyle = textColor;
                    ctx.fillText(volumeText, scaledX, cellCenterY);
                    
                    drawnCount++;
                });
                
                // Draw outer border around entire grid
                ctx.strokeStyle = 'rgba(100, 100, 100, 0.5)';
                ctx.lineWidth = 1.5 * pixelRatio;
                ctx.strokeRect(candleLeft, startY, candleWidth, effectiveCandleHeight);
            }
            
            // Draw clean DELTA below candle if significant
            if (Math.abs(delta) > 0.01) {
                const deltaColor = delta > 0 ? 'rgba(16, 185, 129, 0.8)' : 'rgba(239, 68, 68, 0.8)';
                const deltaText = delta > 0 ? `Δ+${delta.toFixed(1)}` : `Δ${delta.toFixed(1)}`;
                const deltaY = scaledLowY + 15 * pixelRatio;
                
                // Just text, no background
                ctx.fillStyle = deltaColor;
                ctx.font = `${7 * pixelRatio}px -apple-system, BlinkMacSystemFont, monospace`;
                ctx.textAlign = 'center';
                ctx.fillText(deltaText, scaledX, deltaY);
            }
            
            // Draw clean POC marker if available
            if (pocPrice && candleData.pointOfControl) {
                const pocY = series.priceToCoordinate(candleData.pointOfControl);
                if (pocY !== null && pocY !== undefined) {
                    const scaledPocY = pocY * pixelRatio;
                    
                    // Minimal POC line
                    ctx.strokeStyle = 'rgba(34, 211, 238, 0.6)';
                    ctx.lineWidth = 1.5 * pixelRatio;
                    ctx.setLineDash([4 * pixelRatio, 2 * pixelRatio]);
                    ctx.beginPath();
                    ctx.moveTo(scaledX - candleWidth / 2 - 2 * pixelRatio, scaledPocY);
                    ctx.lineTo(scaledX + candleWidth / 2 + 2 * pixelRatio, scaledPocY);
                    ctx.stroke();
                    ctx.setLineDash([]);
                }
            }
            
            drawnCount++;
            
            // Draw VAH, VAL, POC, and metrics for this candle
            if (this._source._showValueArea || this._source._showPOC || this._source._showMetrics) {
                const candleLeftX = scaledX - candleWidth / 2;
                const candleRightX = scaledX + candleWidth / 2;
                
                // Draw Value Area High (VAH) - clean style
                if (this._source._showValueArea && candleData.valueAreaHigh) {
                    const vahY = series.priceToCoordinate(candleData.valueAreaHigh);
                    if (vahY !== null && vahY !== undefined) {
                        const scaledVahY = vahY * pixelRatio;
                        ctx.strokeStyle = 'rgba(251, 191, 36, 0.5)';
                        ctx.lineWidth = 1 * pixelRatio;
                        ctx.setLineDash([3 * pixelRatio, 2 * pixelRatio]);
                        ctx.beginPath();
                        ctx.moveTo(candleLeftX, scaledVahY);
                        ctx.lineTo(candleRightX, scaledVahY);
                        ctx.stroke();
                        ctx.setLineDash([]);
                    }
                }
                
                // Draw Value Area Low (VAL) - clean style
                if (this._source._showValueArea && candleData.valueAreaLow) {
                    const valY = series.priceToCoordinate(candleData.valueAreaLow);
                    if (valY !== null && valY !== undefined) {
                        const scaledValY = valY * pixelRatio;
                        ctx.strokeStyle = 'rgba(251, 191, 36, 0.5)';
                        ctx.lineWidth = 1 * pixelRatio;
                        ctx.setLineDash([3 * pixelRatio, 2 * pixelRatio]);
                        ctx.beginPath();
                        ctx.moveTo(candleLeftX, scaledValY);
                        ctx.lineTo(candleRightX, scaledValY);
                        ctx.stroke();
                        ctx.setLineDash([]);
                    }
                }
                
                // POC handled above - skip duplicate
                // This section is for other metrics only
                
                // Metrics handled by delta below candle - keep it clean
            }
        });

        ctx.restore();
    }
}

export class FootprintPrimitive {
    constructor(chart, series) {
        this._chart = chart;
        this._series = series;
        this._data = [];
        this._fontSize = 8; // Optimized for performance
        this._buyColor = 'rgb(16, 185, 129)'; // Brighter green
        this._sellColor = 'rgb(239, 68, 68)'; // Red
        this._neutralColor = 'rgb(156, 163, 175)'; // gray
        this._vahColor = 'rgba(251, 191, 36, 0.8)'; // Brighter yellow for VAH
        this._valColor = 'rgba(251, 191, 36, 0.8)'; // Brighter yellow for VAL
        this._pocColor = 'rgba(34, 211, 238, 1)'; // Brighter cyan for POC
        this._showValueArea = true; // Show VAH/VAL lines
        this._showPOC = true; // Show Point of Control
        this._showMetrics = true; // Show delta/volume text
        this._showVolumeBars = true; // Show volume bars at bottom
        this._minVolumeToShow = 0.001; // Don't show very small volumes
        this._renderer = new FootprintRenderer(this);
        this._paneViews = [new FootprintPaneView(this)];
        console.log('[Footprint] Primitive constructed with chart/series');
    }

    updateData(data) {
        this._data = data;
        console.log('[Footprint] Data updated:', data?.length, 'candles');
        if (data && data.length > 0) {
            console.log('[Footprint] Sample candle data:', {
                time: data[0].time,
                priceCount: data[0].volumeByPrice?.length || 0,
                firstPrice: data[0].volumeByPrice?.[0]
            });
        }
        this.updateAllViews();
    }

    updateAllViews() {
        this._paneViews.forEach(pv => pv.update());
    }

    paneViews() {
        return this._paneViews;
    }

    setShowValueArea(show) {
        this._showValueArea = show;
    }
    
    setShowPOC(show) {
        this._showPOC = show;
    }
    
    setShowMetrics(show) {
        this._showMetrics = show;
    }
    
    setShowVolumeBars(show) {
        this._showVolumeBars = show;
    }
}

/**
 * Volume Profile Primitive - Shows horizontal volume bars at price levels
 * Better for showing overall volume distribution
 */
export class VolumeProfilePrimitive {
    constructor() {
        this._data = [];
        this._buyColor = 'rgba(34, 197, 94, 0.6)';
        this._sellColor = 'rgba(239, 68, 68, 0.6)';
        this._maxBarWidth = 100; // pixels
    }

    updateData(data) {
        this._data = data;
    }

    draw(target) {
        target.useBitmapCoordinateSpace(scope => this._drawImpl(scope, target));
    }

    _drawImpl(scope, target) {
        const ctx = scope.context;

        if (!this._data || this._data.length === 0) {
            return;
        }

        const chart = target.chart;
        const series = target.series;
        
        if (!chart || !series) return;

        const timeScale = chart.timeScale();
        const priceScale = series.priceScale();
        
        if (!timeScale || !priceScale) return;

        ctx.save();

        // Aggregate all volume data
        const volumeAtPrice = {};
        let maxVolume = 0;

        this._data.forEach(candleData => {
            if (!candleData.volumeByPrice) return;
            
            candleData.volumeByPrice.forEach(({ price, buyVolume, sellVolume }) => {
                const priceKey = price.toFixed(2);
                if (!volumeAtPrice[priceKey]) {
                    volumeAtPrice[priceKey] = { buy: 0, sell: 0 };
                }
                volumeAtPrice[priceKey].buy += buyVolume;
                volumeAtPrice[priceKey].sell += sellVolume;
                
                const total = volumeAtPrice[priceKey].buy + volumeAtPrice[priceKey].sell;
                maxVolume = Math.max(maxVolume, total);
            });
        });

        // Draw volume profile bars
        Object.keys(volumeAtPrice).forEach(priceKey => {
            const price = parseFloat(priceKey);
            const { buy, sell } = volumeAtPrice[priceKey];
            const total = buy + sell;
            
            const y = priceScale.priceToCoordinate(price);
            if (y === null || y === undefined) return;

            const barWidth = (total / maxVolume) * this._maxBarWidth;
            const barHeight = 3;

            // Draw sell volume (red)
            const sellWidth = (sell / total) * barWidth;
            ctx.fillStyle = this._sellColor;
            ctx.fillRect(scope.bitmapSize.width - barWidth, y - barHeight / 2, sellWidth, barHeight);

            // Draw buy volume (green)
            const buyWidth = (buy / total) * barWidth;
            ctx.fillStyle = this._buyColor;
            ctx.fillRect(scope.bitmapSize.width - barWidth + sellWidth, y - barHeight / 2, buyWidth, barHeight);
        });

        ctx.restore();
    }
}

